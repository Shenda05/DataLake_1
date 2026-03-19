package com.datalake.platform.task;

import com.datalake.platform.common.util.GeneratedKeyUtils;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class TaskLogService {

    private final JdbcTemplate jdbcTemplate;

    public TaskLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TaskLogSummary> list() {
        return jdbcTemplate.query(
            """
                select l.log_id,l.task_id,l.task_type,l.target_id,l.start_time,l.end_time,l.status,l.execution_summary,l.error_message,l.duration,
                       coalesce(t.task_name, case when l.task_type = 'GOVERNANCE' then '手动治理执行' else '手动任务执行' end) as task_name
                from task_log l
                left join task_def t on t.task_id = l.task_id
                order by l.log_id desc
            """,
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
                rs.getString("error_message")
            )
        );
    }

    public TaskLogDetail detail(Long logId) {
        TaskLogDetail detail = jdbcTemplate.query(
            """
                select l.log_id,l.task_id,l.task_type,l.target_id,l.start_time,l.end_time,l.status,l.execution_summary,l.error_message,l.duration,l.operator_user,l.create_time,
                       coalesce(t.task_name, case when l.task_type = 'GOVERNANCE' then '手动治理执行' else '手动任务执行' end) as task_name
                from task_log l
                left join task_def t on t.task_id = l.task_id
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
                    (Long) rs.getObject("operator_user"),
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
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into task_log(task_id,task_type,target_id,start_time,end_time,status,execution_summary,error_message,duration,operator_user,create_time)
                    values(?,?,?,?,?,?,?,?,?,?,?)
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
            statement.setLong(9, durationSeconds(startTime, endTime));
            statement.setObject(10, operatorUser);
            statement.setTimestamp(11, Timestamp.from(Instant.now()));
            return statement;
        }, keyHolder);
        return GeneratedKeyUtils.getLongId(keyHolder, "log_id");
    }

    private long durationSeconds(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return Math.max(0, java.time.Duration.between(startTime, endTime).getSeconds());
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
        String errorMessage
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
        Long operatorUser,
        String createTime
    ) {
    }
}
