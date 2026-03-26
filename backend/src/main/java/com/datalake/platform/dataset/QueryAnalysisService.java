package com.datalake.platform.dataset;

import com.datalake.platform.common.web.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QueryAnalysisService {

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
        List<FilterCondition> filters = List.of(new FilterCondition(body.field(), body.operator(), body.value()));
        return datasetTableService.filter(
            dataset.physicalTableName(),
            datasetService.metadata(body.datasetId()),
            filters,
            body.pageNum() == null ? 1 : body.pageNum(),
            body.pageSize() == null ? 10 : body.pageSize()
        );
    }

    public List<Map<String, Object>> sql(SqlQueryRequest body) {
        DatasetService.DatasetDetail dataset = datasetService.detail(body.datasetId());
        String sql = normalizeSql(dataset, body.sql());
        return datasetTableService.executeSql(sql);
    }

    public TabularExportService.ExportedFile exportFilter(FilterQueryRequest body, String format) {
        PageResponse<Map<String, Object>> response = filter(body);
        List<Map<String, Object>> rows = response.records();
        DatasetService.DatasetDetail dataset = datasetService.detail(body.datasetId());
        List<String> headers = resolveHeaders(rows, datasetService.metadata(body.datasetId()).stream().map(DatasetService.MetaFieldRecord::fieldName).toList());
        return tabularExportService.export(dataset.datasetName() + "_filter_result", headers, rows, format);
    }

    public TabularExportService.ExportedFile exportSql(SqlQueryRequest body, String format) {
        DatasetService.DatasetDetail dataset = datasetService.detail(body.datasetId());
        List<Map<String, Object>> rows = datasetTableService.executeSql(normalizeSql(dataset, body.sql()));
        List<String> headers = resolveHeaders(rows, List.of());
        return tabularExportService.export(dataset.datasetName() + "_sql_result", headers, rows, format);
    }

    private String normalizeSql(DatasetService.DatasetDetail dataset, String rawSql) {
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
        if (!lower.contains(" limit ")) {
            sql = sql + " limit 100";
        }
        return sql;
    }

    private List<String> resolveHeaders(List<Map<String, Object>> rows, List<String> fallbackHeaders) {
        if (!rows.isEmpty()) {
            return new ArrayList<>(rows.get(0).keySet());
        }
        return fallbackHeaders;
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

    private String safeJson(Map<String, Object> row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (Exception exception) {
            return row.toString();
        }
    }

    public record FilterCondition(String field, String operator, String value) {
    }

    public record FilterQueryRequest(Long datasetId, String field, String operator, String value, Integer pageNum, Integer pageSize) {
    }

    public record SqlQueryRequest(Long datasetId, String sql) {
    }

    public record SummaryResponse(Long datasetId, long recordCount, long nullCount, long duplicateCount) {
    }

    public record ChartPoint(String name, int value) {
    }
}
