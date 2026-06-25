package com.datalake.platform.task;

import com.datalake.platform.common.util.GeneratedKeyUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class TaskLogService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TaskLogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<TaskLogSummary> list() {
        return list(null, null, null, null, null, null);
    }

    public List<TaskLogSummary> list(
        String taskType,
        String status,
        Long operatorUser,
        String startTime,
        String endTime,
        String keyword
    ) {
        StringBuilder sql = new StringBuilder(
            """
                select l.log_id,l.task_id,l.task_type,l.target_id,l.start_time,l.end_time,l.status,l.execution_summary,l.error_message,l.duration,
                       l.operator_user,operator_user.username as operator_name,
                       coalesce(t.task_name, case when l.task_type = 'GOVERNANCE' then '手动治理执行' else '手动任务执行' end) as task_name
                from task_log l
                left join task_def t on t.task_id = l.task_id
                left join sys_user operator_user on operator_user.user_id = l.operator_user
                where 1 = 1
            """
        );
        List<Object> args = new ArrayList<>();

        if (taskType != null && !taskType.isBlank()) {
            sql.append(" and l.task_type = ?");
            args.add(taskType.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and l.status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (operatorUser != null) {
            sql.append(" and l.operator_user = ?");
            args.add(operatorUser);
        }
        LocalDateTime start = parseLogTime(startTime, false);
        LocalDateTime end = parseLogTime(endTime, true);
        if (start != null) {
            sql.append(" and l.start_time >= ?");
            args.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" and l.start_time <= ?");
            args.add(Timestamp.valueOf(end));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            sql.append(" and (lower(coalesce(t.task_name, '')) like ? or lower(coalesce(l.execution_summary, '')) like ? or lower(coalesce(l.error_message, '')) like ?)");
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        sql.append(" order by l.log_id desc");
        return jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> new TaskLogSummary(
                rs.getLong("log_id"),
                (Long) rs.getObject("task_id"),
                rs.getString("task_name"),
                rs.getString("task_type"),
                (Long) rs.getObject("target_id"),
                rs.getString("status"),
                rs.getTimestamp("start_time") == null ? null : rs.getTimestamp("start_time").toLocalDateTime().toString(),
                rs.getTimestamp("end_time") == null ? null : rs.getTimestamp("end_time").toLocalDateTime().toString(),
                rs.getLong("duration"),
                rs.getString("execution_summary"),
                rs.getString("error_message"),
                (Long) rs.getObject("operator_user"),
                rs.getString("operator_name")
            ),
            args.toArray()
        );
    }

    public TaskLogDetail detail(Long logId) {
        TaskLogDetail detail = jdbcTemplate.query(
            """
                select l.log_id,l.task_id,l.task_type,l.target_id,l.start_time,l.end_time,l.status,l.execution_summary,l.error_message,l.duration,l.operator_user,l.create_time,
                       l.input_params,l.execution_steps,l.failure_reason,
                       operator_user.username as operator_name,
                       coalesce(t.task_name, case when l.task_type = 'GOVERNANCE' then '手动治理执行' else '手动任务执行' end) as task_name
                from task_log l
                left join task_def t on t.task_id = l.task_id
                left join sys_user operator_user on operator_user.user_id = l.operator_user
                where l.log_id = ?
            """,
            rs -> rs.next()
                ? new TaskLogDetail(
                    rs.getLong("log_id"),
                    (Long) rs.getObject("task_id"),
                    rs.getString("task_name"),
                    rs.getString("task_type"),
                    (Long) rs.getObject("target_id"),
                    rs.getString("status"),
                    rs.getTimestamp("start_time") == null ? null : rs.getTimestamp("start_time").toLocalDateTime().toString(),
                    rs.getTimestamp("end_time") == null ? null : rs.getTimestamp("end_time").toLocalDateTime().toString(),
                    rs.getLong("duration"),
                    rs.getString("execution_summary"),
                    rs.getString("error_message"),
                    parseInputParams(rs.getString("input_params")),
                    parseExecutionSteps(rs.getString("execution_steps")),
                    parseFailureReason(rs.getString("failure_reason")),
                    (Long) rs.getObject("operator_user"),
                    rs.getString("operator_name"),
                    rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime().toString()
                )
                : null,
            logId
        );
        if (detail == null) {
            throw new IllegalArgumentException("任务日志不存在: " + logId);
        }
        return detail;
    }

    public Long record(
        Long taskId,
        String taskType,
        Long targetId,
        Instant startTime,
        Instant endTime,
        String status,
        String executionSummary,
        String errorMessage,
        Long operatorUser
    ) {
        return record(taskId, taskType, targetId, startTime, endTime, status, executionSummary, errorMessage, operatorUser, Map.of(), List.of(), null);
    }

    public Long record(
        Long taskId,
        String taskType,
        Long targetId,
        Instant startTime,
        Instant endTime,
        String status,
        String executionSummary,
        String errorMessage,
        Long operatorUser,
        Map<String, Object> inputParams,
        List<ExecutionStep> executionSteps,
        FailureReason failureReason
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into task_log(task_id,task_type,target_id,start_time,end_time,status,execution_summary,error_message,input_params,execution_steps,failure_reason,duration,operator_user,create_time)
                    values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setObject(1, taskId);
            statement.setString(2, taskType);
            statement.setObject(3, targetId);
            statement.setTimestamp(4, Timestamp.from(startTime));
            statement.setTimestamp(5, endTime == null ? null : Timestamp.from(endTime));
            statement.setString(6, status);
            statement.setString(7, executionSummary);
            statement.setString(8, errorMessage);
            statement.setString(9, clipJson(toJson(inputParams)));
            statement.setString(10, clipJson(toJson(executionSteps)));
            statement.setString(11, clipJson(toJson(failureReason)));
            statement.setLong(12, durationSeconds(startTime, endTime));
            statement.setObject(13, operatorUser);
            statement.setTimestamp(14, Timestamp.from(Instant.now()));
            return statement;
        }, keyHolder);
        return GeneratedKeyUtils.getLongId(keyHolder, "log_id");
    }

    private Map<String, Object> parseInputParams(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception exception) {
            return Map.of("raw", raw, "parseError", exception.getMessage());
        }
    }

    private List<ExecutionStep> parseExecutionSteps(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<ExecutionStep>>() {
            });
        } catch (Exception exception) {
            return List.of(new ExecutionStep(0, "PARSE_ERROR", "FAILED", "执行步骤解析失败: " + exception.getMessage()));
        }
    }

    private FailureReason parseFailureReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, FailureReason.class);
        } catch (Exception exception) {
            return new FailureReason("RAW_FAILURE_REASON", "UNKNOWN", "结构化失败原因解析失败", raw);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"serializeError\":\"" + exception.getMessage() + "\"}";
        }
    }

    private String clipJson(String json) {
        if (json == null || json.length() <= 3900) {
            return json;
        }
        return json.substring(0, 3900) + "...";
    }

    private long durationSeconds(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return Math.max(0, java.time.Duration.between(startTime, endTime).getSeconds());
    }

    private LocalDateTime parseLogTime(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        try {
            if (normalized.length() <= 10) {
                LocalDate date = LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(normalized.replace(" ", "T"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception exception) {
            throw new IllegalArgumentException("时间格式错误，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    public record TaskLogSummary(
        Long logId,
        Long taskId,
        String taskName,
        String taskType,
        Long targetId,
        String status,
        String startTime,
        String endTime,
        Long duration,
        String executionSummary,
        String errorMessage,
        Long operatorUser,
        String operatorName
    ) {
    }

    public record TaskLogDetail(
        Long logId,
        Long taskId,
        String taskName,
        String taskType,
        Long targetId,
        String status,
        String startTime,
        String endTime,
        Long duration,
        String executionSummary,
        String errorMessage,
        Map<String, Object> inputParams,
        List<ExecutionStep> executionSteps,
        FailureReason failureReason,
        Long operatorUser,
        String operatorName,
        String createTime
    ) {
    }

    public record ExecutionStep(Integer stepIndex, String stepName, String status, String detail) {
    }

    public record FailureReason(String code, String step, String reason, String rawMessage) {
    }
}
