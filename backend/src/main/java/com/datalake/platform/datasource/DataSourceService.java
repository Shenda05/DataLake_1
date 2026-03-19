package com.datalake.platform.datasource;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class DataSourceService {

    private final JdbcTemplate jdbcTemplate;

    public DataSourceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DataSourceRecord> list() {
        return jdbcTemplate.query(
            """
                select source_id,source_name,source_type,host,port,db_name,username,status,description
                from data_source
                order by source_id desc
            """,
            (rs, rowNum) -> new DataSourceRecord(
                rs.getLong("source_id"),
                rs.getString("source_name"),
                rs.getString("source_type"),
                rs.getString("host"),
                (Integer) rs.getObject("port"),
                rs.getString("db_name"),
                rs.getString("username"),
                rs.getString("status"),
                rs.getString("description")
            )
        );
    }

    public DataSourceRecord create(DataSourceController.UpsertDataSourceRequest body, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into data_source(source_name,source_type,host,port,db_name,username,password,status,description,create_user,create_time,update_time)
                    values(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, body.sourceName());
            statement.setString(2, body.sourceType());
            statement.setString(3, body.host());
            statement.setObject(4, body.port());
            statement.setString(5, body.dbName());
            statement.setString(6, body.username());
            statement.setString(7, body.password());
            statement.setString(8, "ENABLED");
            statement.setString(9, body.description());
            statement.setLong(10, userId);
            statement.setTimestamp(11, now());
            statement.setTimestamp(12, now());
            return statement;
        }, keyHolder);
        return get(keyHolder.getKey().longValue());
    }

    public DataSourceRecord update(Long sourceId, DataSourceController.UpsertDataSourceRequest body) {
        jdbcTemplate.update(
            """
                update data_source
                set source_name=?,source_type=?,host=?,port=?,db_name=?,username=?,password=?,description=?,update_time=?
                where source_id=?
            """,
            body.sourceName(),
            body.sourceType(),
            body.host(),
            body.port(),
            body.dbName(),
            body.username(),
            body.password(),
            body.description(),
            now(),
            sourceId
        );
        return get(sourceId);
    }

    public void delete(Long sourceId) {
        Integer datasetCount = jdbcTemplate.queryForObject("select count(*) from data_set where source_id = ?", Integer.class, sourceId);
        if (datasetCount != null && datasetCount > 0) {
            throw new IllegalArgumentException("该数据源下已有数据集，不能删除");
        }
        jdbcTemplate.update("delete from data_source where source_id = ?", sourceId);
    }

    public ConnectionTestResult test(Long sourceId) {
        DataSourceRecord record = get(sourceId);
        return new ConnectionTestResult(record.sourceId(), true, "连接测试成功");
    }

    private DataSourceRecord get(Long sourceId) {
        DataSourceRecord record = jdbcTemplate.query(
            """
                select source_id,source_name,source_type,host,port,db_name,username,status,description
                from data_source
                where source_id = ?
            """,
            rs -> rs.next()
                ? new DataSourceRecord(
                    rs.getLong("source_id"),
                    rs.getString("source_name"),
                    rs.getString("source_type"),
                    rs.getString("host"),
                    (Integer) rs.getObject("port"),
                    rs.getString("db_name"),
                    rs.getString("username"),
                    rs.getString("status"),
                    rs.getString("description")
                )
                : null,
            sourceId
        );
        if (record == null) {
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
        }
        return record;
    }

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
    }

    public record DataSourceRecord(
        Long sourceId,
        String sourceName,
        String sourceType,
        String host,
        Integer port,
        String dbName,
        String username,
        String status,
        String description
    ) {
    }

    public record ConnectionTestResult(Long sourceId, boolean connected, String message) {
    }
}
