package com.datalake.platform.dataset;

import com.datalake.platform.common.web.PageResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        StringBuilder where = new StringBuilder(" where 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        int index = 0;
        for (QueryAnalysisService.FilterCondition filter : filters) {
            if (filter.field() == null || filter.field().isBlank() || filter.value() == null || filter.value().isBlank()) {
                continue;
            }
            DatasetService.MetaFieldRecord column = columns.stream()
                .filter(meta -> meta.fieldName().equals(filter.field()) || meta.physicalColumnName().equals(filter.field()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("字段不存在: " + filter.field()));
            String name = "value" + index;
            switch (filter.operator().toUpperCase()) {
                case "EQ" -> {
                    where.append(" and ").append(column.physicalColumnName()).append(" = :").append(name);
                    params.addValue(name, filter.value());
                }
                case "GT" -> {
                    where.append(" and ").append(column.physicalColumnName()).append(" > :").append(name);
                    params.addValue(name, filter.value());
                }
                case "LT" -> {
                    where.append(" and ").append(column.physicalColumnName()).append(" < :").append(name);
                    params.addValue(name, filter.value());
                }
                case "LIKE" -> {
                    where.append(" and lower(concat('', ").append(column.physicalColumnName()).append(")) like :").append(name);
                    params.addValue(name, "%" + filter.value().toLowerCase() + "%");
                }
                default -> throw new IllegalArgumentException("不支持的过滤操作: " + filter.operator());
            }
            index += 1;
        }
        int offset = Math.max(pageNum - 1, 0) * pageSize;
        Long total = namedParameterJdbcTemplate.queryForObject("select count(*) from " + tableName + where, params, Long.class);
        List<Map<String, Object>> records = queryRecords(
            "select * from " + tableName + where + " order by row_id limit " + pageSize + " offset " + offset,
            params,
            columns
        );
        return new PageResponse<>(pageNum, pageSize, total == null ? 0 : total, records);
    }

    public List<Map<String, Object>> executeSql(String sql) {
        return jdbcTemplate.queryForList(sql);
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
}
