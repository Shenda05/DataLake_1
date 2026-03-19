package com.datalake.platform.task;

import com.datalake.platform.datasource.DataImportService;
import com.datalake.platform.governance.GovernanceService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private final JdbcTemplate jdbcTemplate;
    private final DataImportService dataImportService;
    private final GovernanceService governanceService;
    private final ThreadPoolTaskScheduler scheduler;
    private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public TaskService(JdbcTemplate jdbcTemplate, DataImportService dataImportService, GovernanceService governanceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataImportService = dataImportService;
        this.governanceService = governanceService;
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(2);
        this.scheduler.setThreadNamePrefix("task-scheduler-");
        this.scheduler.initialize();
        initializeSchedules();
    }

    public List<TaskResponse> list() {
        return jdbcTemplate.query(
            """
                select task_id,task_name,task_type,target_id,cron_expr,status,retry_policy,description,next_run_time,last_run_time
                from task_def order by task_id desc
            """,
            (rs, rowNum) -> new TaskResponse(
                rs.getLong("task_id"),
                rs.getString("task_name"),
                rs.getString("task_type"),
                rs.getLong("target_id"),
                rs.getString("cron_expr"),
                rs.getString("status"),
                rs.getInt("retry_policy"),
                rs.getString("description"),
                rs.getTimestamp("next_run_time") == null ? null : rs.getTimestamp("next_run_time").toLocalDateTime().toString(),
                rs.getTimestamp("last_run_time") == null ? null : rs.getTimestamp("last_run_time").toLocalDateTime().toString()
            )
        );
    }

    public TaskResponse create(TaskController.SaveTaskRequest body, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime nextRunTime = nextRun(body.cronExpr());
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into task_def(task_name,task_type,target_id,cron_expr,status,payload,retry_policy,create_user,description,next_run_time,create_time,update_time)
                    values(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, body.taskName());
            statement.setString(2, body.taskType());
            statement.setLong(3, body.targetId());
            statement.setString(4, body.cronExpr());
            statement.setString(5, body.status() == null || body.status().isBlank() ? "ENABLED" : body.status());
            statement.setString(6, null);
            statement.setInt(7, 1);
            statement.setLong(8, userId);
            statement.setString(9, body.description());
            statement.setTimestamp(10, nextRunTime == null ? null : Timestamp.valueOf(nextRunTime));
            statement.setTimestamp(11, now());
            statement.setTimestamp(12, now());
            return statement;
        }, keyHolder);
        Long taskId = keyHolder.getKey().longValue();
        TaskResponse task = get(taskId);
        if ("ENABLED".equalsIgnoreCase(task.status())) {
            register(task);
        }
        return task;
    }

    public TaskResponse update(Long taskId, TaskController.SaveTaskRequest body) {
        cancel(taskId);
        LocalDateTime nextRunTime = nextRun(body.cronExpr());
        jdbcTemplate.update(
            """
                update task_def
                set task_name=?,task_type=?,target_id=?,cron_expr=?,status=?,description=?,next_run_time=?,update_time=?
                where task_id=?
            """,
            body.taskName(),
            body.taskType(),
            body.targetId(),
            body.cronExpr(),
            body.status() == null || body.status().isBlank() ? "ENABLED" : body.status(),
            body.description(),
            nextRunTime == null ? null : Timestamp.valueOf(nextRunTime),
            now(),
            taskId
        );
        TaskResponse task = get(taskId);
        if ("ENABLED".equalsIgnoreCase(task.status())) {
            register(task);
        }
        return task;
    }

    public TaskResponse trigger(Long taskId, Long userId) {
        executeTask(taskId, userId);
        return get(taskId);
    }

    public TaskResponse pause(Long taskId) {
        cancel(taskId);
        jdbcTemplate.update("update task_def set status = ?, update_time = ? where task_id = ?", "PAUSED", now(), taskId);
        return get(taskId);
    }

    public TaskResponse resume(Long taskId, Long userId) {
        jdbcTemplate.update(
            "update task_def set status = ?, next_run_time = ?, update_time = ? where task_id = ?",
            "ENABLED",
            Timestamp.valueOf(nextRun(get(taskId).cronExpr())),
            now(),
            taskId
        );
        TaskResponse task = get(taskId);
        register(task);
        return task;
    }

    public List<TaskLogResponse> logs() {
        return jdbcTemplate.query(
            """
                select log_id,task_id,task_type,target_id,start_time,end_time,status,execution_summary,error_message,duration
                from task_log order by log_id desc
            """,
            (rs, rowNum) -> new TaskLogResponse(
                rs.getLong("log_id"),
                rs.getObject("task_id") == null ? null : rs.getLong("task_id"),
                rs.getString("task_type"),
                rs.getObject("target_id") == null ? null : rs.getLong("target_id"),
                rs.getTimestamp("start_time").toLocalDateTime().toString(),
                rs.getTimestamp("end_time") == null ? null : rs.getTimestamp("end_time").toLocalDateTime().toString(),
                rs.getString("status"),
                rs.getString("execution_summary"),
                rs.getString("error_message"),
                rs.getLong("duration")
            )
        );
    }

    public TaskLogResponse logDetail(Long logId) {
        return jdbcTemplate.query(
            """
                select log_id,task_id,task_type,target_id,start_time,end_time,status,execution_summary,error_message,duration
                from task_log where log_id = ?
            """,
            rs -> rs.next()
                ? new TaskLogResponse(
                    rs.getLong("log_id"),
                    rs.getObject("task_id") == null ? null : rs.getLong("task_id"),
                    rs.getString("task_type"),
                    rs.getObject("target_id") == null ? null : rs.getLong("target_id"),
                    rs.getTimestamp("start_time").toLocalDateTime().toString(),
                    rs.getTimestamp("end_time") == null ? null : rs.getTimestamp("end_time").toLocalDateTime().toString(),
                    rs.getString("status"),
                    rs.getString("execution_summary"),
                    rs.getString("error_message"),
                    rs.getLong("duration")
                )
                : null,
            logId
        );
    }

    public TaskResponse get(Long taskId) {
        return jdbcTemplate.query(
            """
                select task_id,task_name,task_type,target_id,cron_expr,status,retry_policy,description,next_run_time,last_run_time
                from task_def where task_id = ?
            """,
            rs -> rs.next()
                ? new TaskResponse(
                    rs.getLong("task_id"),
                    rs.getString("task_name"),
                    rs.getString("task_type"),
                    rs.getLong("target_id"),
                    rs.getString("cron_expr"),
                    rs.getString("status"),
                    rs.getInt("retry_policy"),
                    rs.getString("description"),
                    rs.getTimestamp("next_run_time") == null ? null : rs.getTimestamp("next_run_time").toLocalDateTime().toString(),
                    rs.getTimestamp("last_run_time") == null ? null : rs.getTimestamp("last_run_time").toLocalDateTime().toString()
                )
                : null,
            taskId
        );
    }

    private void initializeSchedules() {
        List<TaskResponse> enabledTasks = jdbcTemplate.query(
            "select task_id,task_name,task_type,target_id,cron_expr,status,retry_policy,description,next_run_time,last_run_time from task_def where status='ENABLED'",
            (rs, rowNum) -> new TaskResponse(
                rs.getLong("task_id"),
                rs.getString("task_name"),
                rs.getString("task_type"),
                rs.getLong("target_id"),
                rs.getString("cron_expr"),
                rs.getString("status"),
                rs.getInt("retry_policy"),
                rs.getString("description"),
                rs.getTimestamp("next_run_time") == null ? null : rs.getTimestamp("next_run_time").toLocalDateTime().toString(),
                rs.getTimestamp("last_run_time") == null ? null : rs.getTimestamp("last_run_time").toLocalDateTime().toString()
            )
        );
        enabledTasks.forEach(this::register);
    }

    private void register(TaskResponse task) {
        cancel(task.taskId());
        ScheduledFuture<?> future = scheduler.schedule(
            () -> executeTask(task.taskId(), 1L),
            new CronTrigger(task.cronExpr())
        );
        futures.put(task.taskId(), future);
        jdbcTemplate.update("update task_def set next_run_time = ?, update_time = ? where task_id = ?", Timestamp.valueOf(nextRun(task.cronExpr())), now(), task.taskId());
    }

    private void cancel(Long taskId) {
        ScheduledFuture<?> future = futures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void executeTask(Long taskId, Long userId) {
        TaskResponse task = get(taskId);
        if (task == null) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        Long logId = insertLog(task.taskId(), task.taskType(), task.targetId(), userId);
        try {
            jdbcTemplate.update("update task_def set status = ?, last_run_time = ?, update_time = ? where task_id = ?", "RUNNING", now(), now(), taskId);
            switch (task.taskType()) {
                case "IMPORT" -> dataImportService.replayImport(task.targetId(), userId);
                case "GOVERNANCE" -> governanceService.executeSavedFlow(task.targetId(), userId, taskId);
                default -> throw new IllegalArgumentException("暂不支持的任务类型: " + task.taskType());
            }
            completeLog(logId, "SUCCESS", "任务执行成功", null, startedAt);
            jdbcTemplate.update(
                "update task_def set status = ?, last_run_time = ?, next_run_time = ?, update_time = ? where task_id = ?",
                "ENABLED",
                now(),
                Timestamp.valueOf(nextRun(task.cronExpr())),
                now(),
                taskId
            );
        } catch (Exception exception) {
            completeLog(logId, "FAILED", "任务执行失败", exception.getMessage(), startedAt);
            jdbcTemplate.update(
                "update task_def set status = ?, last_run_time = ?, next_run_time = ?, update_time = ? where task_id = ?",
                "ENABLED",
                now(),
                Timestamp.valueOf(nextRun(task.cronExpr())),
                now(),
                taskId
            );
        }
    }

    private Long insertLog(Long taskId, String taskType, Long targetId, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into task_log(task_id,task_type,target_id,start_time,status,operator_user,create_time)
                    values(?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, taskId);
            statement.setString(2, taskType);
            statement.setLong(3, targetId);
            statement.setTimestamp(4, now());
            statement.setString(5, "RUNNING");
            statement.setLong(6, userId);
            statement.setTimestamp(7, now());
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void completeLog(Long logId, String status, String summary, String errorMessage, long startedAt) {
        jdbcTemplate.update(
            "update task_log set end_time = ?, status = ?, execution_summary = ?, error_message = ?, duration = ? where log_id = ?",
            now(),
            status,
            summary,
            errorMessage,
            System.currentTimeMillis() - startedAt,
            logId
        );
    }

    private LocalDateTime nextRun(String cronExpr) {
        return CronExpression.parse(cronExpr).next(LocalDateTime.now());
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    public record TaskResponse(
        Long taskId,
        String taskName,
        String taskType,
        Long targetId,
        String cronExpr,
        String status,
        Integer retryPolicy,
        String description,
        String nextRunTime,
        String lastRunTime
    ) {
    }

    public record TaskLogResponse(
        Long logId,
        Long taskId,
        String taskType,
        Long targetId,
        String startTime,
        String endTime,
        String status,
        String executionSummary,
        String errorMessage,
        Long duration
    ) {
    }
}

