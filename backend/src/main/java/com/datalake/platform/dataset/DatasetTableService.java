package com.datalake.platform.dataset;

import com.datalake.platform.common.web.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatasetTableService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DatasetTableService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public void createPhysicalTable(String tableName, List<DatasetService.MetaFieldRecord> columns) {
        String columnSql = columns.stream()
            .map(column -> column.physicalColumnName() + " " + sqlType(column.fieldType()))
            .collect(Collectors.joining(", "));
        jdbcTemplate.execute("create table " + tableName + " (row_id bigint auto_increment primary key, " + columnSql + ")");
    }

    public void insertRows(String tableName, List<DatasetService.MetaFieldRecord> columns, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        String columnNames = columns.stream().map(DatasetService.MetaFieldRecord::physicalColumnName).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(column -> ":" + column.physicalColumnName()).collect(Collectors.joining(", "));
        String sql = "insert into " + tableName + " (" + columnNames + ") values (" + placeholders + ")";
        List<MapSqlParameterSource> batch = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            for (DatasetService.MetaFieldRecord column : columns) {
                params.addValue(column.physicalColumnName(), row.get(column.fieldName()));
            }
            batch.add(params);
        }
        namedParameterJdbcTemplate.batchUpdate(sql, batch.toArray(new MapSqlParameterSource[0]));
    }

    public PageResponse<Map<String, Object>> preview(String tableName, List<DatasetService.MetaFieldRecord> columns, int pageNum, int pageSize) {
        return preview(tableName, columns, pageNum, pageSize, null, null);
    }

    public PageResponse<Map<String, Object>> preview(
        String tableName,
        List<DatasetService.MetaFieldRecord> columns,
        int pageNum,
        int pageSize,
        String field,
        String keyword
    ) {
        int offset = Math.max(pageNum - 1, 0) * pageSize;
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = keywordWhereClause(columns, field, keyword, params);
        Long total = namedParameterJdbcTemplate.queryForObject("select count(*) from " + tableName + whereClause, params, Long.class);
        List<Map<String, Object>> records = queryRecords("select * from " + tableName + whereClause + " order by row_id limit " + pageSize + " offset " + offset, params, columns);
        return new PageResponse<>(pageNum, pageSize, total == null ? 0 : total, records);
    }

    public PageResponse<Map<String, Object>> filter(
        String tableName,
        List<DatasetService.MetaFieldRecord> columns,
        List<QueryAnalysisService.FilterCondition> filters,
        int pageNum,
        int pageSize
    ) {
        return filter(tableName, columns, filters, "AND", null, "ASC", pageNum, pageSize);
    }

    public PageResponse<Map<String, Object>> filter(
        String tableName,
        List<DatasetService.MetaFieldRecord> columns,
        List<QueryAnalysisService.FilterCondition> filters,
        String logic,
        String sortField,
        String sortOrder,
        int pageNum,
        int pageSize
    ) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();
        String logicKeyword = "OR".equalsIgnoreCase(logic) ? " OR " : " AND ";
        int index = 0;
        for (QueryAnalysisService.FilterCondition filter : filters) {
            if (filter == null || filter.field() == null || filter.field().isBlank() || filter.operator() == null || filter.operator().isBlank()) {
                continue;
            }
            DatasetService.MetaFieldRecord column = resolveColumn(columns, filter.field());
            String operator = filter.operator().toUpperCase(Locale.ROOT);
            String name = "value" + index;
            String nameTo = "valueTo" + index;
            String value = filter.value() == null ? "" : filter.value().trim();
            String valueTo = filter.valueTo() == null ? "" : filter.valueTo().trim();
            switch (operator) {
                case "EQ", "GT", "GTE", "LT", "LTE" -> {
                    if (value.isBlank()) {
                        continue;
                    }
                    String sqlOperator = switch (operator) {
                        case "EQ" -> "=";
                        case "GT" -> ">";
                        case "GTE" -> ">=";
                        case "LT" -> "<";
                        default -> "<=";
                    };
                    clauses.add(column.physicalColumnName() + " " + sqlOperator + " :" + name);
                    params.addValue(name, value);
                }
                case "LIKE" -> {
                    if (value.isBlank()) {
                        continue;
                    }
                    clauses.add("lower(concat('', " + column.physicalColumnName() + ")) like :" + name);
                    params.addValue(name, "%" + value.toLowerCase(Locale.ROOT) + "%");
                }
                case "BETWEEN", "TIME_RANGE" -> {
                    if (value.isBlank() || valueTo.isBlank()) {
                        throw new IllegalArgumentException(operator + " 查询条件需要同时填写起始值和结束值");
                    }
                    if ("TIME_RANGE".equals(operator)) {
                        validateTimeRangeValue(value);
                        validateTimeRangeValue(valueTo);
                    }
                    clauses.add("(" + column.physicalColumnName() + " >= :" + name + " and " + column.physicalColumnName() + " <= :" + nameTo + ")");
                    params.addValue(name, value);
                    params.addValue(nameTo, valueTo);
                }
                default -> throw new IllegalArgumentException("不支持的过滤操作: " + operator);
            }
            index += 1;
        }
        if (clauses.isEmpty()) {
            throw new IllegalArgumentException("请至少提供一个有效查询条件");
        }
        where.append(" and (").append(String.join(logicKeyword, clauses)).append(")");
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = Math.max(safePageNum - 1, 0) * safePageSize;
        String orderByClause = buildOrderByClause(columns, sortField, sortOrder);
        Long total = namedParameterJdbcTemplate.queryForObject("select count(*) from " + tableName + where, params, Long.class);
        List<Map<String, Object>> records = queryRecords(
            "select * from " + tableName + where + orderByClause + " limit " + safePageSize + " offset " + offset,
            params,
            columns
        );
        return new PageResponse<>(safePageNum, safePageSize, total == null ? 0 : total, records);
    }

    public List<Map<String, Object>> executeSql(String sql) {
        return executeSqlWithColumns(sql).rows();
    }

    public SqlExecutionResult executeSqlWithColumns(String sql) {
        return jdbcTemplate.query(sql, rs -> {
            java.sql.ResultSetMetaData metaData = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                columns.add(metaData.getColumnLabel(i));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                for (String column : columns) {
                    row.put(column, rs.getObject(column));
                }
                rows.add(row);
            }
            return new SqlExecutionResult(columns, rows);
        });
    }

    public List<Map<String, Object>> fetchAll(String tableName, List<DatasetService.MetaFieldRecord> columns, String field, String keyword) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = keywordWhereClause(columns, field, keyword, params);
        return queryRecords("select * from " + tableName + whereClause + " order by row_id", params, columns);
    }

    public void dropTable(String tableName) {
        jdbcTemplate.execute("drop table if exists " + tableName);
    }

    private List<Map<String, Object>> queryRecords(String sql, List<DatasetService.MetaFieldRecord> columns) {
        return queryRecords(sql, new MapSqlParameterSource(), columns);
    }

    private List<Map<String, Object>> queryRecords(String sql, MapSqlParameterSource params, List<DatasetService.MetaFieldRecord> columns) {
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (DatasetService.MetaFieldRecord column : columns) {
                row.put(column.fieldName(), rs.getObject(column.physicalColumnName()));
            }
            return row;
        });
    }

    private String sqlType(String fieldType) {
        return switch (fieldType.toUpperCase()) {
            case "BIGINT" -> "BIGINT";
            case "DOUBLE" -> "DOUBLE";
            case "BOOLEAN" -> "BOOLEAN";
            default -> "VARCHAR(1024)";
        };
    }

    private String keywordWhereClause(
        List<DatasetService.MetaFieldRecord> columns,
        String field,
        String keyword,
        MapSqlParameterSource params
    ) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        params.addValue("keyword", "%" + keyword.toLowerCase() + "%");
        if (field != null && !field.isBlank()) {
            DatasetService.MetaFieldRecord column = columns.stream()
                .filter(meta -> meta.fieldName().equals(field) || meta.physicalColumnName().equals(field))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("筛选字段不存在: " + field));
            return " where lower(concat('', " + column.physicalColumnName() + ")) like :keyword";
        }
        String orClause = columns.stream()
            .map(column -> "lower(concat('', " + column.physicalColumnName() + ")) like :keyword")
            .collect(Collectors.joining(" or "));
        return " where (" + orClause + ")";
    }

    private DatasetService.MetaFieldRecord resolveColumn(List<DatasetService.MetaFieldRecord> columns, String field) {
        return columns.stream()
            .filter(meta -> meta.fieldName().equalsIgnoreCase(field) || meta.physicalColumnName().equalsIgnoreCase(field))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("字段不存在: " + field));
    }

    private String buildOrderByClause(List<DatasetService.MetaFieldRecord> columns, String sortField, String sortOrder) {
        String direction = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        if (sortField == null || sortField.isBlank()) {
            return " order by row_id " + direction;
        }
        DatasetService.MetaFieldRecord column = resolveColumn(columns, sortField);
        if ("row_id".equalsIgnoreCase(column.physicalColumnName())) {
            return " order by row_id " + direction;
        }
        return " order by " + column.physicalColumnName() + " " + direction + ", row_id asc";
    }

    private void validateTimeRangeValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("时间范围条件值不能为空");
        }
        String text = value.trim();
        try {
            if (text.length() == 10) {
                LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            } else {
                LocalDateTime.parse(text.replace(" ", "T"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间范围格式无效: " + value + "，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    public record SqlExecutionResult(List<String> columns, List<Map<String, Object>> rows) {
        public SqlExecutionResult {
            columns = columns == null ? List.of() : List.copyOf(columns);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }
}
