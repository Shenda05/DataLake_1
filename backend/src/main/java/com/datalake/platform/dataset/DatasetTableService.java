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
        int offset = Math.max(pageNum - 1, 0) * pageSize;
        Long total = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
        List<Map<String, Object>> records = queryRecords(
            "select * from " + tableName + " order by row_id limit " + pageSize + " offset " + offset,
            columns
        );
        return new PageResponse<>(pageNum, pageSize, total == null ? 0 : total, records);
    }

    public void dropTable(String tableName) {
        jdbcTemplate.execute("drop table if exists " + tableName);
    }

    private List<Map<String, Object>> queryRecords(String sql, List<DatasetService.MetaFieldRecord> columns) {
        return namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> {
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
}
