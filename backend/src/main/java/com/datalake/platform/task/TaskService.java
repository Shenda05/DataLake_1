package com.datalake.platform.task;

import com.datalake.platform.datasource.DataImportService;
import com.datalake.platform.governance.GovernanceService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final JdbcTemplate jdbcTemplate;
    private final GovernanceService governanceService;
    private final DataImportService dataImportService;
    private final TaskLogService taskLogService;
    private final Set<Long> runningTasks = ConcurrentHashMap.newKeySet();

    public TaskService(
        JdbcTemplate jdbcTemplate,
        GovernanceService governanceService,
        DataImportService dataImportService,
        TaskLogService taskLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.governanceService = governanceService;
        this.dataImportService = dataImportService;
        this.taskLogService = taskLogService;
    }

    public List<TaskSummary> list() {
        return jdbcTemplate.query(
            """
                select task_id,task_name,task_type,target_id,cron_expr,status,retry_policy,description,next_run_time,last_run_time,create_user
                from task_def
                order by task_id desc
            """,
            (rs, rowNum) -> new TaskSummary(
                rs.getLong("task_id"),
                rs.getString("task_name"),
                rs.getString("task_type"),
                rs.getLong("target_id"),
                resolveTargetName(rs.getString("task_type"), rs.getLong("target_id")),
                rs.getString("cron_expr"),
                rs.getString("status"),
                rs.getInt("retry_policy"),
                rs.getString("description"),
                rs.getTimestamp("next_run_time") == null ? null : rs.getTimestamp("next_run_time").toLocalDateTime().toString(),
                rs.getTimestamp("last_run_time") == null ? null : rs.getTimestamp("last_run_time").toLocalDateTime().toString(),
                (Long) rs.getObject("create_user")
            )
        );
    }

    @Transactional
    public TaskSummary create(SaveTaskCommand command, Long userId) {
        validateTask(command.taskType(), command.targetId(), command.cronExpr());
        String status = normalizeTaskStatus(command.status());
        Integer retryPolicy = command.retryPolicy() == null ? 1 : Math.max(1, command.retryPolicy());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp nextRunTime = "PAUSED".equals(status) ? null : nextRunTime(command.cronExpr(), LocalDateTime.now());
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into task_def(task_name,task_type,target_id,cron_expr,status,retry_policy,create_user,payload,description,next_run_time,last_run_time,create_time,update_time)
                    values(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, command.taskName());
            statement.setString(2, normalizeTaskType(command.taskType()));
            statement.setLong(3, command.targetId());
            statement.setString(4, command.cronExpr());
            statement.setString(5, status);
            statement.setInt(6, retryPolicy);
            statement.setLong(7, userId);
            statement.setString(8, null);
            statement.setString(9, command.description());
            statement.setTimestamp(10, nextRunTime);
            statement.setTimestamp(11, null);
            statement.setTimestamp(12, now());
            statement.setTimestamp(13, now());
            return statement;
        }, keyHolder);
        return detail(com.datalake.platform.common.util.GeneratedKeyUtils.getLongId(keyHolder, "task_id"));
    }

    @Transactional
    public TaskSummary update(Long taskId, SaveTaskCommand command) {
        validateTask(command.taskType(), command.targetId(), command.cronExpr());
        TaskRecord existing = loadTask(taskId);
        String status = normalizeTaskStatus(command.status());
        Integer retryPolicy = command.retryPolicy() == null ? existing.retryPolicy() : Math.max(1, command.retryPolicy());
        Timestamp nextRunTime = "PAUSED".equals(status) ? null : nextRunTime(command.cronExpr(), LocalDateTime.now());
        jdbcTemplate.update(
            """
                update task_def
                set task_name = ?, task_type = ?, target_id = ?, cron_expr = ?, status = ?, retry_policy = ?, description = ?, next_run_time = ?, update_time = ?
                where task_id = ?
            """,
            command.taskName(),
            normalizeTaskType(command.taskType()),
            command.targetId(),
            command.cronExpr(),
            status,
            retryPolicy,
            command.description(),
            nextRunTime,
            now(),
            taskId
        );
        return detail(taskId);
    }

    @Transactional
    public TaskActionResponse pause(Long taskId) {
        loadTask(taskId);
        jdbcTemplate.update("update task_def set status = 'PAUSED', next_run_time = null, update_time = ? where task_id = ?", now(), taskId);
        return new TaskActionResponse(taskId, "PAUSED", null, "任务已暂停", null);
    }

    @Transactional
    public TaskActionResponse resume(Long taskId) {
        TaskRecord task = loadTask(taskId);
        Timestamp nextRunTime = nextRunTime(task.cronExpr(), LocalDateTime.now());
        jdbcTemplate.update("update task_def set status = 'ENABLED', next_run_time = ?, update_time = ? where task_id = ?", nextRunTime, now(), taskId);
        return new TaskActionResponse(taskId, "ENABLED", null, "任务已恢复", toText(nextRunTime));
    }

    @Transactional
    public TaskActionResponse trigger(Long taskId, Long userId) {
        return executeTask(taskId, userId, true);
    }

    @Transactional
    public void executeDueTasks() {
        List<Long> dueTaskIds = jdbcTemplate.query(
            "select task_id from task_def where status = 'ENABLED' and next_run_time is not null and next_run_time <= ? order by next_run_time asc",
            (rs, rowNum) -> rs.getLong("task_id"),
            now()
        );
        for (Long taskId : dueTaskIds) {
            TaskRecord task = loadTask(taskId);
            Long operatorUser = task.createUser() == null ? 0L : task.createUser();
            executeTask(taskId, operatorUser, false);
        }
    }

    public TaskSummary detail(Long taskId) {
        TaskRecord task = loadTask(taskId);
        return new TaskSummary(
            task.taskId(),
            task.taskName(),
            task.taskType(),
            task.targetId(),
            resolveTargetName(task.taskType(), task.targetId()),
            task.cronExpr(),
            task.status(),
            task.retryPolicy(),
            task.description(),
            toText(task.nextRunTime()),
            toText(task.lastRunTime()),
            task.createUser()
        );
    }

    private TaskActionResponse executeTask(Long taskId, Long userId, boolean manualTrigger) {
        TaskRecord task = loadTask(taskId);
        if (!runningTasks.add(taskId)) {
            return new TaskActionResponse(taskId, "RUNNING", null, "任务正在执行，请稍后重试", toText(task.nextRunTime()));
        }
        Instant start = Instant.now();
        String originalStatus = task.status();
        jdbcTemplate.update(
            "update task_def set status = 'RUNNING', last_run_time = ?, update_time = ? where task_id = ?",
            Timestamp.from(start),
            now(),
            taskId
        );

        try {
            int attempts = Math.max(1, task.retryPolicy());
            Exception lastException = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    ExecutionOutcome outcome = runTask(task, userId);
                    Instant end = Instant.now();
                    Long logId = taskLogService.record(
                        task.taskId(),
                        task.taskType(),
                        task.targetId(),
                        start,
                        end,
                        "SUCCESS",
                        outcome.summary() + "；执行次数 " + attempt,
                        null,
                        userId
                    );
                    String resetStatus = "PAUSED".equalsIgnoreCase(originalStatus) && manualTrigger ? "PAUSED" : "ENABLED";
                    Timestamp nextRunTime = "ENABLED".equals(resetStatus) ? nextRunTime(task.cronExpr(), LocalDateTime.now()) : null;
                    jdbcTemplate.update(
                        "update task_def set status = ?, next_run_time = ?, last_run_time = ?, update_time = ? where task_id = ?",
                        resetStatus,
                        nextRunTime,
                        Timestamp.from(end),
                        now(),
                        task.taskId()
                    );
                    return new TaskActionResponse(task.taskId(), "SUCCESS", logId, outcome.summary(), toText(nextRunTime));
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
            Instant end = Instant.now();
            Long logId = taskLogService.record(
                task.taskId(),
                task.taskType(),
                task.targetId(),
                start,
                end,
                "FAILED",
                "任务执行失败",
                lastException == null ? "未知错误" : lastException.getMessage(),
                userId
            );
            String resetStatus = "PAUSED".equalsIgnoreCase(originalStatus) && manualTrigger ? "PAUSED" : "ENABLED";
            Timestamp nextRunTime = "ENABLED".equals(resetStatus) ? nextRunTime(task.cronExpr(), LocalDateTime.now()) : null;
            jdbcTemplate.update(
                "update task_def set status = ?, next_run_time = ?, last_run_time = ?, update_time = ? where task_id = ?",
                resetStatus,
                nextRunTime,
                Timestamp.from(end),
                now(),
                task.taskId()
            );
            return new TaskActionResponse(task.taskId(), "FAILED", logId, lastException == null ? "任务执行失败" : lastException.getMessage(), toText(nextRunTime));
        } finally {
            runningTasks.remove(taskId);
        }
    }

    private ExecutionOutcome runTask(TaskRecord task, Long userId) {
        return switch (task.taskType().toUpperCase(Locale.ROOT)) {
            case "IMPORT" -> {
                DataImportService.ImportResult result = dataImportService.rerunImport(task.targetId(), userId, task.taskName());
                yield new ExecutionOutcome("导入完成，生成数据集 " + result.datasetName() + "，记录数 " + result.recordCount());
            }
            case "GOVERNANCE" -> {
                GovernanceService.ExecutionResult result = governanceService.executeSavedFlow(task.targetId(), userId, task.taskName());
                yield new ExecutionOutcome(result.summary());
            }
            default -> throw new IllegalArgumentException("当前版本仅支持 IMPORT 和 GOVERNANCE 任务");
        };
    }

    private void validateTask(String taskType, Long targetId, String cronExpr) {
        String normalizedType = normalizeTaskType(taskType);
        try {
            CronExpression.parse(cronExpr);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cron 表达式无效: " + exception.getMessage(), exception);
        }
        if ("IMPORT".equals(normalizedType)) {
            dataImportService.detail(targetId);
            return;
        }
        if ("GOVERNANCE".equals(normalizedType)) {
            governanceService.flow(targetId);
            return;
        }
        throw new IllegalArgumentException("当前版本仅支持 IMPORT 和 GOVERNANCE 任务");
    }

    private String normalizeTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("taskType 不能为空");
        }
        return taskType.toUpperCase(Locale.ROOT);
    }

    private String normalizeTaskStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ENABLED";
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        if (!List.of("ENABLED", "PAUSED").contains(normalized)) {
            throw new IllegalArgumentException("任务状态仅支持 ENABLED 或 PAUSED");
        }
        return normalized;
    }

    private String resolveTargetName(String taskType, Long targetId) {
        if ("IMPORT".equalsIgnoreCase(taskType)) {
            return jdbcTemplate.query(
                "select dataset_name from import_record where import_id = ?",
                rs -> rs.next() ? rs.getString("dataset_name") : null,
                targetId
            );
        }
        if ("GOVERNANCE".equalsIgnoreCase(taskType)) {
            return jdbcTemplate.query(
                "select flow_name from governance_flow where flow_id = ?",
                rs -> rs.next() ? rs.getString("flow_name") : null,
                targetId
            );
        }
        return "未知目标";
    }

    private TaskRecord loadTask(Long taskId) {
        TaskRecord task = jdbcTemplate.query(
            """
                select task_id,task_name,task_type,target_id,cron_expr,status,retry_policy,description,next_run_time,last_run_time,create_user
                from task_def
                where task_id = ?
            """,
            rs -> rs.next()
                ? new TaskRecord(
                    rs.getLong("task_id"),
                    rs.getString("task_name"),
                    rs.getString("task_type"),
                    rs.getLong("target_id"),
                    rs.getString("cron_expr"),
                    rs.getString("status"),
                    rs.getInt("retry_policy"),
                    rs.getString("description"),
                    rs.getTimestamp("next_run_time"),
                    rs.getTimestamp("last_run_time"),
                    (Long) rs.getObject("create_user")
                )
                : null,
            taskId
        );
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    private Timestamp nextRunTime(String cronExpr, LocalDateTime baseTime) {
        LocalDateTime next = CronExpression.parse(cronExpr).next(baseTime);
        return next == null ? null : Timestamp.valueOf(next);
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private String toText(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }

    public record SaveTaskCommand(
        String taskName,
        String taskType,
        Long targetId,
        String cronExpr,
        Integer retryPolicy,
        String description,
        String status
    ) {
    }

    public record TaskSummary(
        Long taskId,
        String taskName,
        String taskType,
        Long targetId,
        String targetName,
        String cronExpr,
        String status,
        Integer retryPolicy,
        String description,
        String nextRunTime,
        String lastRunTime,
        Long createUser
    ) {
    }

    public record TaskActionResponse(Long taskId, String status, Long logId, String message, String nextRunTime) {
    }

    private record TaskRecord(
        Long taskId,
        String taskName,
        String taskType,
        Long targetId,
        String cronExpr,
        String status,
        Integer retryPolicy,
        String description,
        Timestamp nextRunTime,
        Timestamp lastRunTime,
        Long createUser
    ) {
    }

    private record ExecutionOutcome(String summary) {
    }
}
