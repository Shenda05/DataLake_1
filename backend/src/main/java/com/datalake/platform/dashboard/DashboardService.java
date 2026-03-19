package com.datalake.platform.dashboard;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OverviewResponse overview() {
        return new OverviewResponse(
            count("select count(*) from data_source"),
            count("select count(*) from data_set"),
            count("select count(*) from data_set where cast(create_time as date) = current_date"),
            count("select count(*) from task_def"),
            count("select count(*) from task_def where status = 'RUNNING'"),
            count("select count(*) from task_log where status = 'SUCCESS'"),
            count("select count(*) from task_log where status = 'FAILED'")
        );
    }

    public List<TrendPoint> taskTrend() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<LocalDate, Integer> counts = new TreeMap<>();
        for (int i = 0; i < 7; i++) {
            counts.put(start.plusDays(i), 0);
        }
        jdbcTemplate.query(
            "select start_time from task_log where start_time is not null",
            rs -> {
                Timestamp value = rs.getTimestamp("start_time");
                if (value == null) {
                    return;
                }
                LocalDate day = value.toLocalDateTime().toLocalDate();
                if (!day.isBefore(start) && !day.isAfter(today)) {
                    counts.computeIfPresent(day, (ignored, count) -> count + 1);
                }
            }
        );
        return counts.entrySet().stream()
            .map(entry -> new TrendPoint(entry.getKey().toString(), entry.getValue()))
            .toList();
    }

    public List<RecentTaskItem> recentTasks() {
        return jdbcTemplate.query(
            """
                select t.task_id,t.task_name,t.task_type,t.status,t.next_run_time
                from task_def t
                order by t.update_time desc
                limit 5
            """,
            (rs, rowNum) -> new RecentTaskItem(
                rs.getLong("task_id"),
                rs.getString("task_name"),
                rs.getString("task_type"),
                rs.getString("status"),
                rs.getTimestamp("next_run_time") == null ? null : rs.getTimestamp("next_run_time").toLocalDateTime().toString()
            )
        );
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    public record OverviewResponse(
        int dataSources,
        int datasets,
        int newDatasetsToday,
        int totalTasks,
        int runningTasks,
        int successTasks,
        int failedTasks
    ) {
    }

    public record TrendPoint(String day, int total) {
    }

    public record RecentTaskItem(Long taskId, String taskName, String taskType, String status, String nextRunTime) {
    }
}
