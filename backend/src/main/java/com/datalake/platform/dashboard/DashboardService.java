package com.datalake.platform.dashboard;

import com.datalake.platform.common.domain.BusinessDomainCatalog;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

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
            count("select count(*) from task_log where status = 'FAILED'"),
            countRecentFailedTasks()
        );
    }

    // [Ecom-MVP Completed] 新增电商首页聚合指标
    public EcommerceOverviewResponse ecommerce() {
        DatasetSnapshot tradeDataset = latestReadyDataset(BusinessDomainCatalog.DEFAULT_DOMAIN);
        DatasetSnapshot inventoryDataset = latestReadyDataset("INVENTORY");

        List<MetricPoint> orderTrend = emptyLast7Days();
        List<MetricPoint> salesTrend = emptyLast7Days();
        List<RankItem> topProducts = List.of();
        long lowStockCount = 0;

        if (tradeDataset != null) {
            Map<String, String> tradeFields = loadFieldMap(tradeDataset.datasetId());
            String timeColumn = resolveColumn(tradeFields, List.of("order_time", "order_date", "created_at", "create_time", "pay_time"));
            String amountColumn = resolveColumn(tradeFields, List.of("amount", "total_amount", "payment_amount", "order_amount", "paid_amount"));
            String productColumn = resolveColumn(tradeFields, List.of("product_name", "sku_name", "product_id", "sku_id", "goods_name", "name"));
            String quantityColumn = resolveColumn(tradeFields, List.of("quantity", "qty", "buy_count", "count"));
            if (timeColumn != null) {
                List<Map<String, Object>> rows = queryColumns(tradeDataset.tableName(), Arrays.asList(timeColumn, amountColumn, productColumn, quantityColumn));
                orderTrend = aggregateOrderTrend(rows, timeColumn);
                salesTrend = aggregateSalesTrend(rows, timeColumn, amountColumn);
                topProducts = aggregateTopProducts(rows, productColumn, quantityColumn);
            }
        }

        if (inventoryDataset != null) {
            Map<String, String> inventoryFields = loadFieldMap(inventoryDataset.datasetId());
            String stockColumn = resolveColumn(inventoryFields, List.of("stock", "stock_qty", "inventory", "inventory_qty", "available_stock", "quantity"));
            if (stockColumn != null) {
                List<Map<String, Object>> rows = queryColumns(inventoryDataset.tableName(), List.of(stockColumn));
                lowStockCount = rows.stream().map(row -> toDouble(row.get(stockColumn))).filter(value -> value <= 10D).count();
            }
        }

        return new EcommerceOverviewResponse(orderTrend, salesTrend, topProducts, lowStockCount);
    }

    public List<TrendPoint> taskTrend() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<LocalDate, Integer> counts = new TreeMap<>();
        for (int i = 0; i < 7; i++) {
            counts.put(start.plusDays(i), 0);
        }
        jdbcTemplate.query("select start_time from task_log where start_time is not null", rs -> {
            Timestamp timestamp = rs.getTimestamp("start_time");
            if (timestamp == null) {
                return;
            }
            LocalDate day = timestamp.toLocalDateTime().toLocalDate();
            if (!day.isBefore(start) && !day.isAfter(today)) {
                counts.computeIfPresent(day, (ignored, value) -> value + 1);
            }
        });
        return counts.entrySet().stream().map(entry -> new TrendPoint(entry.getKey().toString(), entry.getValue())).toList();
    }

    public List<RecentTaskItem> recentTasks() {
        return jdbcTemplate.query(
            """
                select task_id,task_name,task_type,status,next_run_time
                from task_def
                order by update_time desc
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

    private int countRecentFailedTasks() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Integer value = jdbcTemplate.queryForObject(
            "select count(*) from task_log where status = 'FAILED' and start_time >= ?",
            Integer.class,
            Timestamp.valueOf(since)
        );
        return value == null ? 0 : value;
    }

    private DatasetSnapshot latestReadyDataset(String businessDomain) {
        String normalizedDomain = BusinessDomainCatalog.normalize(businessDomain);
        return jdbcTemplate.query(
            """
                select dataset_id,physical_table_name
                from data_set
                where business_domain = ? and status = 'READY'
                order by update_time desc, dataset_id desc
                limit 1
            """,
            rs -> rs.next() ? new DatasetSnapshot(rs.getLong("dataset_id"), rs.getString("physical_table_name")) : null,
            normalizedDomain
        );
    }

    private Map<String, String> loadFieldMap(Long datasetId) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
                select field_name,physical_column_name
                from meta_field
                where dataset_id = ?
                order by field_order asc, field_id asc
            """,
            rs -> {
                fields.put(rs.getString("field_name"), rs.getString("physical_column_name"));
            },
            datasetId
        );
        return fields;
    }

    private String resolveColumn(Map<String, String> fields, List<String> candidates) {
        if (fields.isEmpty()) {
            return null;
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String logicalName = entry.getKey();
            String physicalName = entry.getValue();
            if (physicalName == null || physicalName.isBlank()) {
                continue;
            }
            if (logicalName != null && !logicalName.isBlank()) {
                normalized.put(logicalName.toLowerCase(Locale.ROOT), physicalName);
            }
            normalized.put(physicalName.toLowerCase(Locale.ROOT), physicalName);
        }
        for (String candidate : candidates) {
            String matched = normalized.get(candidate.toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private List<Map<String, Object>> queryColumns(String tableName, List<String> columns) {
        List<String> selected = columns.stream().filter(column -> column != null && !column.isBlank()).distinct().toList();
        if (selected.isEmpty()) {
            return List.of();
        }
        String safeTable = safeIdentifier(tableName, "table");
        String selectClause = String.join(",", selected.stream().map(column -> "`" + safeIdentifier(column, "column") + "`").toList());
        return jdbcTemplate.queryForList("select " + selectClause + " from `" + safeTable + "`");
    }

    private String safeIdentifier(String identifier, String type) {
        if (identifier == null || identifier.isBlank() || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("非法" + type + "标识: " + identifier);
        }
        return identifier;
    }

    private List<MetricPoint> emptyLast7Days() {
        LocalDate today = LocalDate.now();
        List<MetricPoint> points = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            points.add(new MetricPoint(day.toString(), 0D));
        }
        return points;
    }

    private List<MetricPoint> aggregateOrderTrend(List<Map<String, Object>> rows, String timeColumn) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<LocalDate, Double> counts = new TreeMap<>();
        for (int i = 0; i < 7; i++) {
            counts.put(start.plusDays(i), 0D);
        }
        for (Map<String, Object> row : rows) {
            LocalDate day = toLocalDate(row.get(timeColumn));
            if (day == null || day.isBefore(start) || day.isAfter(today)) {
                continue;
            }
            counts.computeIfPresent(day, (ignored, value) -> value + 1D);
        }
        return counts.entrySet().stream().map(entry -> new MetricPoint(entry.getKey().toString(), entry.getValue())).toList();
    }

    private List<MetricPoint> aggregateSalesTrend(List<Map<String, Object>> rows, String timeColumn, String amountColumn) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<LocalDate, Double> totals = new TreeMap<>();
        for (int i = 0; i < 7; i++) {
            totals.put(start.plusDays(i), 0D);
        }
        for (Map<String, Object> row : rows) {
            LocalDate day = toLocalDate(row.get(timeColumn));
            if (day == null || day.isBefore(start) || day.isAfter(today)) {
                continue;
            }
            double amount = toDouble(row.get(amountColumn));
            totals.computeIfPresent(day, (ignored, value) -> value + amount);
        }
        return totals.entrySet().stream()
            .map(entry -> new MetricPoint(entry.getKey().toString(), Math.round(entry.getValue() * 100D) / 100D))
            .toList();
    }

    private List<RankItem> aggregateTopProducts(List<Map<String, Object>> rows, String productColumn, String quantityColumn) {
        if (productColumn == null) {
            return List.of();
        }
        Map<String, Double> ranking = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String product = String.valueOf(row.get(productColumn) == null ? "UNKNOWN" : row.get(productColumn));
            if (product.isBlank()) {
                product = "UNKNOWN";
            }
            double quantity = quantityColumn == null ? 1D : Math.max(1D, toDouble(row.get(quantityColumn)));
            ranking.merge(product, quantity, Double::sum);
        }
        return ranking.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
            .limit(5)
            .map(entry -> new RankItem(entry.getKey(), Math.round(entry.getValue() * 100D) / 100D))
            .toList();
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        List<DateTimeFormatter> dateTimeFormatters = List.of(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        );
        for (DateTimeFormatter formatter : dateTimeFormatters) {
            try {
                return LocalDateTime.parse(text, formatter).toLocalDate();
            } catch (DateTimeParseException ignored) {
            }
        }
        List<DateTimeFormatter> dateFormatters = List.of(
            DateTimeFormatter.ISO_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.BASIC_ISO_DATE
        );
        for (DateTimeFormatter formatter : dateFormatters) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return 0D;
        }
        String normalized = text.replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) {
            return 0D;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException exception) {
            return 0D;
        }
    }

    public record OverviewResponse(
        int dataSources,
        int datasets,
        int newDatasetsToday,
        int totalTasks,
        int runningTasks,
        int successTasks,
        int failedTasks,
        int recentFailedTasks
    ) {
    }

    public record TrendPoint(String day, int total) {
    }

    public record MetricPoint(String day, double value) {
    }

    public record RankItem(String name, double value) {
    }

    public record EcommerceOverviewResponse(
        List<MetricPoint> orderTrend,
        List<MetricPoint> salesTrend,
        List<RankItem> topProducts,
        long lowStockCount
    ) {
    }

    public record RecentTaskItem(Long taskId, String taskName, String taskType, String status, String nextRunTime) {
    }

    private record DatasetSnapshot(Long datasetId, String tableName) {
    }
}
