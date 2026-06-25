package com.datalake.platform.dataset;

import com.datalake.platform.common.web.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class QueryAnalysisService {

    private static final int MAX_ANALYSIS_ROWS = 20_000;

    private final DatasetService datasetService;
    private final DatasetTableService datasetTableService;
    private final ObjectMapper objectMapper;
    private final TabularExportService tabularExportService;

    public QueryAnalysisService(
        DatasetService datasetService,
        DatasetTableService datasetTableService,
        ObjectMapper objectMapper,
        TabularExportService tabularExportService
    ) {
        this.datasetService = datasetService;
        this.datasetTableService = datasetTableService;
        this.objectMapper = objectMapper;
        this.tabularExportService = tabularExportService;
    }

    public PageResponse<Map<String, Object>> filter(FilterQueryRequest body) {
        DatasetService.DatasetDetail dataset = datasetService.detail(body.datasetId());
        List<DatasetService.MetaFieldRecord> metadata = datasetService.metadata(body.datasetId());
        List<FilterCondition> filters = normalizeFilterConditions(body);
        if (filters.isEmpty()) {
            throw new IllegalArgumentException("请至少提供一个查询条件");
        }
        return datasetTableService.filter(
            dataset.physicalTableName(),
            metadata,
            filters,
            normalizeLogic(body.logic()),
            body.sortField(),
            normalizeSortOrder(body.sortOrder()),
            normalizePageNum(body.pageNum()),
            normalizePageSize(body.pageSize())
        );
    }

    public List<Map<String, Object>> sql(SqlQueryRequest body) {
        DatasetService.DatasetDetail dataset = datasetService.detail(body.datasetId());
        String sql = normalizeSql(dataset, body.sql(), 100);
        return datasetTableService.executeSql(sql);
    }

    public PageResponse<Map<String, Object>> sqlPage(SqlPageQueryRequest body) {
        DatasetService.DatasetDetail dataset = datasetService.detail(body.datasetId());
        String sql = normalizeSql(dataset, body.sql(), null);
        DatasetTableService.SqlExecutionResult result = datasetTableService.executeSqlWithColumns(sql);
        List<Map<String, Object>> sortedRows = sortSqlRows(
            result.rows(),
            result.columns(),
            body.sortField(),
            normalizeSortOrder(body.sortOrder())
        );
        int pageNum = normalizePageNum(body.pageNum());
        int pageSize = normalizePageSize(body.pageSize());
        int fromIndex = Math.max(pageNum - 1, 0) * pageSize;
        int total = sortedRows.size();
        if (fromIndex >= total) {
            return new PageResponse<>(pageNum, pageSize, total, List.of());
        }
        int toIndex = Math.min(fromIndex + pageSize, total);
        return new PageResponse<>(pageNum, pageSize, total, sortedRows.subList(fromIndex, toIndex));
    }

    public TabularExportService.ExportedFile exportFilter(FilterQueryRequest body, String format) {
        DatasetService.DatasetDetail dataset = datasetService.detail(body.datasetId());
        List<DatasetService.MetaFieldRecord> metadata = datasetService.metadata(body.datasetId());
        List<Map<String, Object>> rows;
        String exportScope = normalizeExportScope(body.exportScope());
        if ("ALL".equals(exportScope)) {
            rows = loadRowsWithOptionalFilters(
                dataset,
                metadata,
                normalizeFilterConditions(body),
                body.logic(),
                body.sortField(),
                body.sortOrder(),
                MAX_ANALYSIS_ROWS,
                "导出数据"
            );
        } else {
            rows = filter(body).records();
        }
        List<String> headers = resolveHeaders(rows, metadata.stream().map(DatasetService.MetaFieldRecord::fieldName).toList());
        return tabularExportService.export(dataset.datasetName() + "_filter_result", headers, rows, format);
    }

    public TabularExportService.ExportedFile exportSql(SqlQueryRequest body, String format) {
        DatasetService.DatasetDetail dataset = datasetService.detail(body.datasetId());
        List<Map<String, Object>> rows = datasetTableService.executeSql(normalizeSql(dataset, body.sql(), 100));
        List<String> headers = resolveHeaders(rows, List.of());
        return tabularExportService.export(dataset.datasetName() + "_sql_result", headers, rows, format);
    }

    private String normalizeSql(DatasetService.DatasetDetail dataset, String rawSql, Integer defaultLimit) {
        String sql = rawSql.trim();
        String lower = sql.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select")) {
            throw new IllegalArgumentException("仅支持 SELECT 查询");
        }
        if (lower.contains(";") || lower.contains("--") || lower.contains("/*")) {
            throw new IllegalArgumentException("SQL 中包含非法语句");
        }
        if (lower.contains(" from dataset")) {
            sql = sql.replaceAll("(?i)from\\s+dataset", "from " + dataset.physicalTableName());
            lower = sql.toLowerCase(Locale.ROOT);
        }
        if (!lower.contains("from " + dataset.physicalTableName().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("SQL 只能查询当前数据集对应的单表");
        }
        if (defaultLimit != null && defaultLimit > 0 && !lower.contains(" limit ")) {
            sql = sql + " limit " + defaultLimit;
        }
        return sql;
    }

    private List<String> resolveHeaders(List<Map<String, Object>> rows, List<String> fallbackHeaders) {
        if (!rows.isEmpty()) {
            return new ArrayList<>(rows.get(0).keySet());
        }
        return fallbackHeaders;
    }

    private List<FilterCondition> normalizeFilterConditions(FilterQueryRequest body) {
        if (body.conditions() != null && !body.conditions().isEmpty()) {
            return body.conditions().stream()
                .filter(condition -> condition != null)
                .map(condition -> new FilterCondition(
                    condition.field(),
                    condition.operator(),
                    condition.value(),
                    condition.valueTo()
                ))
                .toList();
        }
        if (body.field() == null || body.field().isBlank()) {
            return List.of();
        }
        return List.of(new FilterCondition(body.field(), body.operator(), body.value(), body.valueTo()));
    }

    private String normalizeLogic(String logic) {
        return "OR".equalsIgnoreCase(logic) ? "OR" : "AND";
    }

    private String normalizeSortOrder(String sortOrder) {
        return "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null ? 1 : Math.max(1, pageNum);
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return 20;
        }
        return Math.max(1, Math.min(pageSize, 200));
    }

    private String normalizeExportScope(String exportScope) {
        return "ALL".equalsIgnoreCase(exportScope) ? "ALL" : "PAGE";
    }

    private List<Map<String, Object>> sortSqlRows(
        List<Map<String, Object>> rows,
        List<String> columns,
        String sortField,
        String sortOrder
    ) {
        if (sortField == null || sortField.isBlank()) {
            return new ArrayList<>(rows);
        }
        String resolvedSortField = resolveSqlSortField(columns, sortField);
        Comparator<Map<String, Object>> comparator = (left, right) -> compareSortValues(
            left.get(resolvedSortField),
            right.get(resolvedSortField)
        );
        if ("DESC".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);
        return sorted;
    }

    private String resolveSqlSortField(List<String> columns, String sortField) {
        for (String column : columns) {
            if (column.equalsIgnoreCase(sortField.trim())) {
                return column;
            }
        }
        throw new IllegalArgumentException("SQL 排序字段不存在: " + sortField);
    }

    private int compareSortValues(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        LocalDateTime leftDateTime = toDateTime(left);
        LocalDateTime rightDateTime = toDateTime(right);
        if (leftDateTime != null && rightDateTime != null) {
            return leftDateTime.compareTo(rightDateTime);
        }
        Double leftNumeric = toNumeric(left);
        Double rightNumeric = toNumeric(right);
        if (leftNumeric != null && rightNumeric != null) {
            return Double.compare(leftNumeric, rightNumeric);
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }

    private Double toNumeric(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        if (value instanceof java.time.LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ignored) {
        }
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10)).atStartOfDay();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public SummaryResponse summary(Long datasetId) {
        DatasetService.DatasetDetail dataset = datasetService.detail(datasetId);
        List<DatasetService.MetaFieldRecord> metadata = datasetService.metadata(datasetId);
        List<Map<String, Object>> rows = datasetTableService.fetchAll(dataset.physicalTableName(), metadata, null, null);
        long nullCount = rows.stream()
            .flatMap(row -> row.values().stream())
            .filter(value -> value == null || value.toString().isBlank())
            .count();
        long duplicateCount = rows.size() - rows.stream().map(this::safeJson).distinct().count();
        return new SummaryResponse(datasetId, rows.size(), nullCount, duplicateCount);
    }

    public List<ChartPoint> charts(Long datasetId) {
        List<DatasetService.MetaFieldRecord> metadata = datasetService.metadata(datasetId);
        if (metadata.isEmpty()) {
            return List.of();
        }
        DatasetService.MetaFieldRecord target = metadata.get(0);
        DatasetService.DatasetDetail dataset = datasetService.detail(datasetId);
        String sql = "select " + target.physicalColumnName() + " as chart_name, count(*) as chart_value from "
            + dataset.physicalTableName() + " group by " + target.physicalColumnName() + " order by chart_value desc limit 10";
        return datasetTableService.executeSql(sql).stream()
            .map(row -> new ChartPoint(String.valueOf(row.get("chart_name")), Integer.parseInt(String.valueOf(row.get("chart_value")))))
            .toList();
    }

    public List<ChartPoint> charts(ChartQueryRequest request) {
        DatasetService.DatasetDetail dataset = datasetService.detail(request.datasetId());
        List<DatasetService.MetaFieldRecord> metadata = datasetService.metadata(request.datasetId());
        if (metadata.isEmpty()) {
            return List.of();
        }
        String dimensionField = resolveChartDimensionField(metadata, request.dimensionField());
        List<Map<String, Object>> rows = loadRowsWithOptionalFilters(
            dataset,
            metadata,
            normalizeMetricConditions(request.conditions()),
            request.logic(),
            null,
            "ASC",
            MAX_ANALYSIS_ROWS,
            "图表统计"
        );
        if (rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                row -> normalizeDimensionValue(row.get(dimensionField)),
                LinkedHashMap::new,
                java.util.stream.Collectors.counting()
            ))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(10)
            .map(entry -> new ChartPoint(entry.getKey(), entry.getValue().intValue()))
            .toList();
    }

    // [已改造完成] 电商指标聚合接口
    public EcommerceMetricResponse ecommerceMetric(EcommerceMetricRequest request) {
        DatasetService.DatasetDetail dataset = datasetService.detail(request.datasetId());
        List<DatasetService.MetaFieldRecord> metadata = datasetService.metadata(request.datasetId());
        List<FilterCondition> filters = normalizeMetricConditions(request.conditions());
        List<Map<String, Object>> rows = loadRowsWithOptionalFilters(
            dataset,
            metadata,
            filters,
            request.logic(),
            null,
            "ASC",
            MAX_ANALYSIS_ROWS,
            "电商指标"
        );
        Map<String, String> fieldMap = buildFieldMap(metadata);
        String metricType = request.metricType() == null ? "ORDER_TREND" : request.metricType().toUpperCase(Locale.ROOT);

        return switch (metricType) {
            case "ORDER_TREND" -> {
                String timeField = requiredField(resolveField(fieldMap, request.timeField(), List.of("order_time", "order_date", "created_at", "create_time")), "订单时间");
                DateRange range = resolveDateRange(filters, timeField);
                List<MetricPoint> trend = aggregateByDate(rows, timeField, null, range.start(), range.end(), 7);
                yield new EcommerceMetricResponse(metricType, trend, toTableRows(trend), buildTrendDescription("订单量趋势", range));
            }
            case "SALES_TREND" -> {
                String timeField = requiredField(resolveField(fieldMap, request.timeField(), List.of("order_time", "order_date", "created_at", "create_time")), "订单时间");
                String valueField = requiredField(resolveField(fieldMap, request.valueField(), List.of("amount", "total_amount", "payment_amount", "order_amount")), "销售额字段");
                DateRange range = resolveDateRange(filters, timeField);
                List<MetricPoint> trend = aggregateByDate(rows, timeField, valueField, range.start(), range.end(), 7);
                yield new EcommerceMetricResponse(metricType, trend, toTableRows(trend), buildTrendDescription("销售额趋势", range));
            }
            case "TOP_PRODUCTS" -> {
                String productField = requiredField(resolveField(fieldMap, request.productField(), List.of("product_name", "sku_name", "product_id", "sku_id", "goods_name")), "商品字段");
                String quantityField = resolveField(fieldMap, request.quantityField(), List.of("quantity", "qty", "buy_count", "count"));
                List<MetricPoint> ranking = aggregateTopN(rows, productField, quantityField, 5);
                yield new EcommerceMetricResponse(metricType, ranking, toTableRows(ranking), "热销商品 Top5");
            }
            case "CATEGORY_SHARE" -> {
                String categoryField = requiredField(resolveField(fieldMap, request.categoryField(), List.of("category", "product_category", "cate_name", "class_name")), "商品分类字段");
                List<MetricPoint> share = aggregateTopN(rows, categoryField, null, 10);
                yield new EcommerceMetricResponse(metricType, share, toTableRows(share), "商品分类占比（按条数）");
            }
            case "LOW_STOCK" -> {
                String stockField = requiredField(resolveField(fieldMap, request.valueField(), List.of("stock", "stock_qty", "inventory", "inventory_qty", "available_stock")), "库存字段");
                double threshold = request.stockThreshold() == null ? 10D : request.stockThreshold();
                List<MetricPoint> warning = aggregateLowStock(rows, stockField, threshold);
                yield new EcommerceMetricResponse(metricType, warning, toTableRows(warning), "低库存预警（库存≤" + threshold + "）");
            }
            default -> throw new IllegalArgumentException("不支持的电商指标类型: " + metricType);
        };
    }

    // [已改造完成] 基础 Join/Union 集成能力（用户+订单/商品+订单/商品+库存）
    public IntegrationQueryResponse integration(IntegrationQueryRequest request) {
        IntegrationComputation computation = computeIntegration(request);
        return new IntegrationQueryResponse(
            computation.mode(),
            computation.columns(),
            computation.merged().size(),
            computation.limited()
        );
    }

    // [已改造完成] 集成结果保存为新数据集（按当前结果落库，受 limit 约束）
    public IntegrationSaveResponse saveIntegrationResult(IntegrationSaveRequest request, Long userId) {
        if (request.outputDatasetName() == null || request.outputDatasetName().isBlank()) {
            throw new IllegalArgumentException("outputDatasetName 不能为空");
        }
        IntegrationComputation computation = computeIntegration(
            new IntegrationQueryRequest(
                request.leftDatasetId(),
                request.rightDatasetId(),
                request.mode(),
                request.leftField(),
                request.rightField(),
                request.limit()
            )
        );
        if (computation.columns().isEmpty()) {
            throw new IllegalArgumentException("当前集成结果没有可保存字段，请调整集成条件后重试");
        }

        String outputDomain = request.outputBusinessDomain();
        if (outputDomain == null || outputDomain.isBlank()) {
            outputDomain = computation.leftDataset().businessDomain();
        }
        if (outputDomain == null || outputDomain.isBlank()) {
            outputDomain = computation.rightDataset().businessDomain();
        }
        if (outputDomain == null || outputDomain.isBlank()) {
            outputDomain = "TRADE";
        }

        Long outputSourceId = computation.leftDataset().sourceId() != null
            ? computation.leftDataset().sourceId()
            : computation.rightDataset().sourceId();
        String mode = computation.mode();
        String storagePath = "integration://left=" + computation.leftDataset().datasetId()
            + "&right=" + computation.rightDataset().datasetId()
            + "&mode=" + mode.toLowerCase(Locale.ROOT);
        List<DatasetService.MetaFieldRecord> outputColumns = buildIntegrationColumns(computation.columns(), computation.limited());
        DatasetService.CreatedDataset created = datasetService.createDatasetFromRows(
            outputSourceId,
            request.outputDatasetName().trim(),
            outputDomain,
            "INTEGRATION_" + mode,
            storagePath,
            "查询分析页集成结果保存数据集（当前结果快照）",
            userId,
            outputColumns,
            computation.limited()
        );

        return new IntegrationSaveResponse(
            created.datasetId(),
            created.datasetName(),
            created.businessDomain(),
            created.recordCount(),
            created.fieldCount(),
            mode
        );
    }

    private Map<String, String> buildFieldMap(List<DatasetService.MetaFieldRecord> metadata) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (DatasetService.MetaFieldRecord field : metadata) {
            map.put(field.fieldName().toLowerCase(Locale.ROOT), field.fieldName());
            map.put(field.physicalColumnName().toLowerCase(Locale.ROOT), field.fieldName());
        }
        return map;
    }

    private String resolveField(Map<String, String> fieldMap, String explicitField, List<String> candidates) {
        if (explicitField != null && !explicitField.isBlank()) {
            String matched = fieldMap.get(explicitField.trim().toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
            throw new IllegalArgumentException("字段不存在: " + explicitField);
        }
        for (String candidate : candidates) {
            String matched = fieldMap.get(candidate.toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private String requiredField(String fieldName, String label) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException(label + " 缺失，请在界面中指定字段映射");
        }
        return fieldName;
    }

    private List<MetricPoint> aggregateByDate(
        List<Map<String, Object>> rows,
        String timeField,
        String valueField,
        LocalDate explicitStart,
        LocalDate explicitEnd,
        int fallbackDays
    ) {
        LocalDate today = LocalDate.now();
        LocalDate start = explicitStart;
        LocalDate end = explicitEnd;
        if (start == null || end == null) {
            end = today;
            start = today.minusDays(Math.max(1, fallbackDays) - 1L);
        }
        if (start.isAfter(end)) {
            LocalDate swapped = start;
            start = end;
            end = swapped;
        }
        Map<LocalDate, Double> bucket = new TreeMap<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            bucket.put(cursor, 0D);
            cursor = cursor.plusDays(1);
        }
        for (Map<String, Object> row : rows) {
            LocalDate date = toDate(row.get(timeField));
            if (date == null || date.isBefore(start) || date.isAfter(end)) {
                continue;
            }
            double increment = valueField == null ? 1D : toDouble(row.get(valueField));
            bucket.computeIfPresent(date, (ignored, oldValue) -> oldValue + increment);
        }
        return bucket.entrySet().stream()
            .map(entry -> new MetricPoint(entry.getKey().toString(), Math.round(entry.getValue() * 100D) / 100D))
            .toList();
    }

    private List<MetricPoint> aggregateTopN(List<Map<String, Object>> rows, String dimensionField, String valueField, int limit) {
        Map<String, Double> bucket = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get(dimensionField) == null ? "UNKNOWN" : row.get(dimensionField));
            if (key.isBlank()) {
                key = "UNKNOWN";
            }
            double increment = valueField == null ? 1D : Math.max(1D, toDouble(row.get(valueField)));
            bucket.merge(key, increment, Double::sum);
        }
        return bucket.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
            .limit(Math.max(1, limit))
            .map(entry -> new MetricPoint(entry.getKey(), Math.round(entry.getValue() * 100D) / 100D))
            .toList();
    }

    private List<MetricPoint> aggregateLowStock(List<Map<String, Object>> rows, String stockField, double threshold) {
        List<MetricPoint> warnings = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            double stock = toDouble(row.get(stockField));
            if (stock <= threshold) {
                String key = row.containsKey("product_name")
                    ? String.valueOf(row.get("product_name"))
                    : row.containsKey("product_id")
                        ? String.valueOf(row.get("product_id"))
                        : "row_" + (i + 1);
                warnings.add(new MetricPoint(key, stock));
            }
        }
        return warnings.stream()
            .sorted(Comparator.comparingDouble(MetricPoint::value))
            .limit(20)
            .toList();
    }

    private List<Map<String, Object>> toTableRows(List<MetricPoint> points) {
        return points.stream()
            .map(point -> {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("name", point.name());
                row.put("value", point.value());
                return (Map<String, Object>) row;
            })
            .toList();
    }

    private String resolveDatasetField(List<DatasetService.MetaFieldRecord> metadata, String field, String fieldName) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        for (DatasetService.MetaFieldRecord item : metadata) {
            if (item.fieldName().equalsIgnoreCase(field) || item.physicalColumnName().equalsIgnoreCase(field)) {
                return item.fieldName();
            }
        }
        throw new IllegalArgumentException("字段不存在: " + field);
    }

    private List<Map<String, Object>> joinRows(
        List<Map<String, Object>> leftRows,
        List<Map<String, Object>> rightRows,
        String leftField,
        String rightField
    ) {
        // [待完善增强] 当前实现为内存 JOIN，后续可下推到数据库侧并增加分页优化。
        LinkedHashMap<String, List<Map<String, Object>>> rightIndex = new LinkedHashMap<>();
        for (Map<String, Object> rightRow : rightRows) {
            String key = joinKey(rightRow.get(rightField));
            rightIndex.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rightRow);
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> leftRow : leftRows) {
            String key = joinKey(leftRow.get(leftField));
            List<Map<String, Object>> matchedRows = rightIndex.getOrDefault(key, List.of());
            for (Map<String, Object> rightRow : matchedRows) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>(leftRow);
                for (Map.Entry<String, Object> entry : rightRow.entrySet()) {
                    String column = row.containsKey(entry.getKey()) ? "right_" + entry.getKey() : entry.getKey();
                    while (row.containsKey(column)) {
                        column = "right_" + column;
                    }
                    row.put(column, entry.getValue());
                }
                merged.add(row);
            }
        }
        return merged;
    }

    private List<Map<String, Object>> unionRows(
        List<Map<String, Object>> leftRows,
        List<Map<String, Object>> rightRows,
        LinkedHashSet<String> columns
    ) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> row : leftRows) {
            merged.add(alignColumns(row, columns));
        }
        for (Map<String, Object> row : rightRows) {
            merged.add(alignColumns(row, columns));
        }
        return merged;
    }

    private Map<String, Object> alignColumns(Map<String, Object> row, LinkedHashSet<String> columns) {
        LinkedHashMap<String, Object> aligned = new LinkedHashMap<>();
        for (String column : columns) {
            aligned.put(column, row.get(column));
        }
        return aligned;
    }

    private String joinKey(Object value) {
        if (value == null) {
            return "__NULL__";
        }
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private LocalDate toDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.time.LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
        }
        try {
            return java.time.LocalDateTime.parse(text).toLocalDate();
        } catch (Exception ignored) {
        }
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10));
            } catch (Exception ignored) {
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
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private String safeJson(Map<String, Object> row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (Exception exception) {
            return row.toString();
        }
    }

    private String resolveChartDimensionField(List<DatasetService.MetaFieldRecord> metadata, String dimensionField) {
        if (dimensionField == null || dimensionField.isBlank()) {
            return metadata.get(0).fieldName();
        }
        return resolveDatasetField(metadata, dimensionField, "dimensionField");
    }

    private String normalizeDimensionValue(Object value) {
        if (value == null) {
            return "UNKNOWN";
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? "UNKNOWN" : text;
    }

    private List<FilterCondition> normalizeMetricConditions(List<FilterCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        return conditions.stream()
            .filter(item -> item != null)
            .filter(item -> item.field() != null && !item.field().isBlank())
            .filter(item -> item.operator() != null && !item.operator().isBlank())
            .toList();
    }

    private List<Map<String, Object>> loadRowsWithOptionalFilters(
        DatasetService.DatasetDetail dataset,
        List<DatasetService.MetaFieldRecord> metadata,
        List<FilterCondition> filters,
        String logic,
        String sortField,
        String sortOrder,
        int limit,
        String usageLabel
    ) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_ANALYSIS_ROWS));
        PageResponse<Map<String, Object>> page = filters == null || filters.isEmpty()
            ? datasetTableService.preview(dataset.physicalTableName(), metadata, 1, safeLimit)
            : datasetTableService.filter(
                dataset.physicalTableName(),
                metadata,
                filters,
                normalizeLogic(logic),
                sortField,
                normalizeSortOrder(sortOrder),
                1,
                safeLimit
            );
        if (page.total() > safeLimit) {
            throw new IllegalArgumentException(usageLabel + "最多支持 " + safeLimit + " 条记录，请先收敛筛选条件");
        }
        return page.records();
    }

    private DateRange resolveDateRange(List<FilterCondition> conditions, String timeField) {
        if (conditions == null || conditions.isEmpty()) {
            return DateRange.EMPTY;
        }
        for (FilterCondition condition : conditions) {
            if (condition == null || condition.field() == null || condition.operator() == null) {
                continue;
            }
            String operator = condition.operator().toUpperCase(Locale.ROOT);
            if (!"TIME_RANGE".equals(operator) && !"BETWEEN".equals(operator)) {
                continue;
            }
            if (!condition.field().equalsIgnoreCase(timeField)) {
                continue;
            }
            LocalDate start = toDate(condition.value());
            LocalDate end = toDate(condition.valueTo());
            if (start != null && end != null) {
                return new DateRange(start, end);
            }
        }
        return DateRange.EMPTY;
    }

    private String buildTrendDescription(String metricName, DateRange range) {
        if (range.start() != null && range.end() != null) {
            return metricName + "（" + range.start() + " ~ " + range.end() + "）";
        }
        return metricName + "（近 7 天）";
    }

    private IntegrationComputation computeIntegration(IntegrationQueryRequest request) {
        DatasetService.DatasetDetail leftDataset = datasetService.detail(request.leftDatasetId());
        DatasetService.DatasetDetail rightDataset = datasetService.detail(request.rightDatasetId());
        List<DatasetService.MetaFieldRecord> leftMeta = datasetService.metadata(request.leftDatasetId());
        List<DatasetService.MetaFieldRecord> rightMeta = datasetService.metadata(request.rightDatasetId());
        List<Map<String, Object>> leftRows = datasetTableService.fetchAll(leftDataset.physicalTableName(), leftMeta, null, null);
        List<Map<String, Object>> rightRows = datasetTableService.fetchAll(rightDataset.physicalTableName(), rightMeta, null, null);

        int limit = request.limit() == null ? 200 : Math.max(1, Math.min(request.limit(), 1000));
        String mode = request.mode() == null ? "JOIN" : request.mode().toUpperCase(Locale.ROOT);

        List<Map<String, Object>> merged;
        List<String> columns;
        if ("JOIN".equals(mode)) {
            String leftField = resolveDatasetField(leftMeta, request.leftField(), "leftField");
            String rightField = resolveDatasetField(rightMeta, request.rightField(), "rightField");
            merged = joinRows(leftRows, rightRows, leftField, rightField);
            columns = resolveJoinColumns(leftMeta, rightMeta);
        } else if ("UNION".equals(mode)) {
            LinkedHashSet<String> unionColumns = resolveUnionColumns(leftMeta, rightMeta, leftRows, rightRows);
            merged = unionRows(leftRows, rightRows, unionColumns);
            columns = new ArrayList<>(unionColumns);
        } else {
            throw new IllegalArgumentException("mode 仅支持 JOIN 或 UNION");
        }

        List<Map<String, Object>> limited = merged.subList(0, Math.min(limit, merged.size()));
        if (columns.isEmpty() && !limited.isEmpty()) {
            columns = new ArrayList<>(limited.get(0).keySet());
        }
        return new IntegrationComputation(mode, columns, merged, limited, leftDataset, rightDataset);
    }

    private List<String> resolveJoinColumns(
        List<DatasetService.MetaFieldRecord> leftMeta,
        List<DatasetService.MetaFieldRecord> rightMeta
    ) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        leftMeta.stream().map(DatasetService.MetaFieldRecord::fieldName).forEach(columns::add);
        for (DatasetService.MetaFieldRecord rightField : rightMeta) {
            String column = rightField.fieldName();
            if (columns.contains(column)) {
                column = "right_" + column;
                while (columns.contains(column)) {
                    column = "right_" + column;
                }
            }
            columns.add(column);
        }
        return new ArrayList<>(columns);
    }

    private LinkedHashSet<String> resolveUnionColumns(
        List<DatasetService.MetaFieldRecord> leftMeta,
        List<DatasetService.MetaFieldRecord> rightMeta,
        List<Map<String, Object>> leftRows,
        List<Map<String, Object>> rightRows
    ) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        leftMeta.stream().map(DatasetService.MetaFieldRecord::fieldName).forEach(columns::add);
        rightMeta.stream().map(DatasetService.MetaFieldRecord::fieldName).forEach(columns::add);
        if (columns.isEmpty()) {
            leftRows.stream().findFirst().ifPresent(row -> columns.addAll(row.keySet()));
            rightRows.stream().findFirst().ifPresent(row -> columns.addAll(row.keySet()));
        }
        return columns;
    }

    private List<DatasetService.MetaFieldRecord> buildIntegrationColumns(List<String> columns, List<Map<String, Object>> rows) {
        LinkedHashSet<String> usedPhysicalColumns = new LinkedHashSet<>();
        List<DatasetService.MetaFieldRecord> result = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String fieldName = columns.get(i);
            String physicalColumn = sanitizePhysicalColumn(fieldName, usedPhysicalColumns);
            Object sample = sampleValue(rows, fieldName);
            result.add(new DatasetService.MetaFieldRecord(
                null,
                null,
                fieldName,
                physicalColumn,
                inferFieldType(sample),
                true,
                sample == null ? null : String.valueOf(sample),
                i + 1
            ));
        }
        return result;
    }

    private String sanitizePhysicalColumn(String fieldName, LinkedHashSet<String> usedPhysicalColumns) {
        String sanitized = com.datalake.platform.common.util.SqlNameUtils.sanitizeColumnName(fieldName);
        if (sanitized.isBlank()) {
            sanitized = "field_col";
        }
        String candidate = sanitized;
        int suffix = 1;
        while (usedPhysicalColumns.contains(candidate)) {
            suffix += 1;
            candidate = sanitized + "_" + suffix;
        }
        usedPhysicalColumns.add(candidate);
        return candidate;
    }

    private Object sampleValue(List<Map<String, Object>> rows, String fieldName) {
        for (Map<String, Object> row : rows) {
            Object value = row.get(fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String inferFieldType(Object sample) {
        if (sample == null) {
            return "STRING";
        }
        if (sample instanceof Byte || sample instanceof Short || sample instanceof Integer || sample instanceof Long) {
            return "BIGINT";
        }
        if (sample instanceof Number) {
            return "DOUBLE";
        }
        if (sample instanceof Boolean) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    public record FilterCondition(String field, String operator, String value, String valueTo) {
    }

    public record FilterQueryRequest(
        Long datasetId,
        String field,
        String operator,
        String value,
        String valueTo,
        List<FilterCondition> conditions,
        String logic,
        String sortField,
        String sortOrder,
        Integer pageNum,
        Integer pageSize,
        String exportScope
    ) {
    }

    public record SqlQueryRequest(Long datasetId, String sql) {
    }

    public record SqlPageQueryRequest(
        Long datasetId,
        String sql,
        String sortField,
        String sortOrder,
        Integer pageNum,
        Integer pageSize
    ) {
    }

    public record EcommerceMetricRequest(
        Long datasetId,
        String metricType,
        String timeField,
        String valueField,
        String categoryField,
        String productField,
        String quantityField,
        Double stockThreshold,
        List<FilterCondition> conditions,
        String logic
    ) {
    }

    public record ChartQueryRequest(
        Long datasetId,
        String dimensionField,
        List<FilterCondition> conditions,
        String logic
    ) {
    }

    public record IntegrationQueryRequest(
        Long leftDatasetId,
        Long rightDatasetId,
        String mode,
        String leftField,
        String rightField,
        Integer limit
    ) {
    }

    public record IntegrationSaveRequest(
        Long leftDatasetId,
        Long rightDatasetId,
        String mode,
        String leftField,
        String rightField,
        Integer limit,
        String outputDatasetName,
        String outputBusinessDomain
    ) {
    }

    public record SummaryResponse(Long datasetId, long recordCount, long nullCount, long duplicateCount) {
    }

    public record ChartPoint(String name, int value) {
    }

    public record MetricPoint(String name, double value) {
    }

    public record EcommerceMetricResponse(
        String metricType,
        List<MetricPoint> chart,
        List<Map<String, Object>> table,
        String description
    ) {
    }

    public record IntegrationQueryResponse(
        String mode,
        List<String> columns,
        int total,
        List<Map<String, Object>> records
    ) {
    }

    public record IntegrationSaveResponse(
        Long datasetId,
        String datasetName,
        String businessDomain,
        int recordCount,
        int fieldCount,
        String mode
    ) {
    }

    private record IntegrationComputation(
        String mode,
        List<String> columns,
        List<Map<String, Object>> merged,
        List<Map<String, Object>> limited,
        DatasetService.DatasetDetail leftDataset,
        DatasetService.DatasetDetail rightDataset
    ) {
    }

    private record DateRange(LocalDate start, LocalDate end) {
        private static final DateRange EMPTY = new DateRange(null, null);
    }
}
