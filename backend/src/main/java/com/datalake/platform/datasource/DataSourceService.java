package com.datalake.platform.datasource;

import com.datalake.platform.common.util.GeneratedKeyUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
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
        return get(GeneratedKeyUtils.getLongId(keyHolder, "source_id"));
    }

    public DataSourceRecord update(Long sourceId, DataSourceController.UpsertDataSourceRequest body) {
        jdbcTemplate.update(
            """
                update data_source
                set source_name=?,source_type=?,host=?,port=?,db_name=?,username=?,
                    password=coalesce(nullif(?, ''), password),
                    description=?,update_time=?
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

    public DataSourceRecord updateStatus(Long sourceId, String status) {
        String normalizedStatus = normalizeSourceStatus(status);
        jdbcTemplate.update(
            "update data_source set status = ?, update_time = ? where source_id = ?",
            normalizedStatus,
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
        DataSourceConnectionInfo record = connectionInfo(sourceId);
        if ("FILE".equalsIgnoreCase(record.sourceType())) {
            return new ConnectionTestResult(record.sourceId(), true, "文件数据源无需远程连接，状态正常");
        }
        try (Connection connection = openConnection(record, record.dbName())) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return new ConnectionTestResult(record.sourceId(), true, "连接测试成功，数据库类型: " + productName);
        } catch (Exception exception) {
            throw new IllegalArgumentException("连接测试失败: " + exception.getMessage(), exception);
        }
    }

    public DataSourceConnectionInfo connectionInfo(Long sourceId) {
        DataSourceConnectionInfo record = jdbcTemplate.query(
            """
                select source_id,source_name,source_type,host,port,db_name,username,password,status,description
                from data_source
                where source_id = ?
            """,
            rs -> rs.next()
                ? new DataSourceConnectionInfo(
                    rs.getLong("source_id"),
                    rs.getString("source_name"),
                    rs.getString("source_type"),
                    rs.getString("host"),
                    (Integer) rs.getObject("port"),
                    rs.getString("db_name"),
                    rs.getString("username"),
                    rs.getString("password"),
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

    public Connection openConnection(DataSourceConnectionInfo profile, String databaseName) throws Exception {
        String sourceType = profile.sourceType() == null ? "" : profile.sourceType().toUpperCase(Locale.ROOT);
        if (!"MYSQL".equals(sourceType)) {
            throw new IllegalArgumentException("当前仅支持 MYSQL 数据库表导入");
        }
        String host = profile.host() == null || profile.host().isBlank() ? "127.0.0.1" : profile.host().trim();
        int port = profile.port() == null ? 3306 : profile.port();
        String catalog = databaseName == null || databaseName.isBlank() ? profile.dbName() : databaseName;
        String url = "jdbc:mysql://" + host + ":" + port;
        if (catalog != null && !catalog.isBlank()) {
            url += "/" + catalog.trim();
        }
        url += "?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(url, profile.username(), profile.password());
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

    private String normalizeSourceStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status 不能为空");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new IllegalArgumentException("数据源状态仅支持 ENABLED 或 DISABLED");
        }
        return normalized;
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

    public record DataSourceConnectionInfo(
        Long sourceId,
        String sourceName,
        String sourceType,
        String host,
        Integer port,
        String dbName,
        String username,
        String password,
        String status,
        String description
    ) {
    }
}
