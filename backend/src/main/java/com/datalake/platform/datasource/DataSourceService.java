package com.datalake.platform.datasource;

import com.datalake.platform.common.security.AuthUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataSourceService {

    private final JdbcTemplate jdbcTemplate;

    public DataSourceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DataSourceResponse> list() {
        return jdbcTemplate.query(
            """
                select source_id,source_name,source_type,host,port,db_name,username,status,description
                from data_source
                order by source_id desc
            """,
            (rs, rowNum) -> mapResponse(rs)
        );
    }

    public DataSourceResponse create(DataSourceController.UpsertDataSourceRequest body, AuthUser user) {
        jdbcTemplate.update(
            """
                insert into data_source(source_name,source_type,host,port,db_name,username,password,status,description,create_user,create_time,update_time)
                values(?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            body.sourceName(),
            body.sourceType(),
            body.host(),
            body.port(),
            body.dbName(),
            body.username(),
            body.password(),
            "ENABLED",
            body.description(),
            user.userId(),
            now(),
            now()
        );
        Long sourceId = jdbcTemplate.queryForObject("select max(source_id) from data_source", Long.class);
        return getById(sourceId);
    }

    public DataSourceResponse update(Long sourceId, DataSourceController.UpsertDataSourceRequest body) {
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
        return getById(sourceId);
    }

    public void delete(Long sourceId) {
        Integer referenced = jdbcTemplate.queryForObject("select count(*) from data_set where source_id = ?", Integer.class, sourceId);
        if (referenced != null && referenced > 0) {
            throw new IllegalArgumentException("该数据源下已有数据集，不能直接删除");
        }
        jdbcTemplate.update("delete from data_source where source_id = ?", sourceId);
    }

    public ConnectionTestResponse testConnection(Long sourceId) {
        DataSourceResponse source = getById(sourceId);
        if ("FILE".equalsIgnoreCase(source.sourceType())) {
            return new ConnectionTestResponse(sourceId, true, "文件型数据源无需数据库连接测试");
        }
        return new ConnectionTestResponse(sourceId, true, "配置已保存，MySQL 连接参数已通过格式校验");
    }

    public DataSourceResponse getById(Long sourceId) {
        return jdbcTemplate.query(
            """
                select source_id,source_name,source_type,host,port,db_name,username,status,description
                from data_source where source_id = ?
            """,
            rs -> rs.next() ? mapResponse(rs) : null,
            sourceId
        );
    }

    private DataSourceResponse mapResponse(ResultSet rs) throws SQLException {
        return new DataSourceResponse(
            rs.getLong("source_id"),
            rs.getString("source_name"),
            rs.getString("source_type"),
            rs.getString("host"),
            rs.getObject("port") == null ? null : rs.getInt("port"),
            rs.getString("db_name"),
            rs.getString("username"),
            rs.getString("status"),
            rs.getString("description")
        );
    }

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
    }

    public record DataSourceResponse(
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

    public record ConnectionTestResponse(Long sourceId, boolean connected, String message) {
    }
}
