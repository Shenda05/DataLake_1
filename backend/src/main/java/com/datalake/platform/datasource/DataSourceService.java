package com.datalake.platform.datasource;

import com.datalake.platform.common.util.GeneratedKeyUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class DataSourceService {

    private final JdbcTemplate jdbcTemplate;
    private final String defaultDuplicateConnectionStrategy;

    public DataSourceService(
        JdbcTemplate jdbcTemplate,
        @Value("${app.data-source.duplicate-connection-strategy:WARN}") String defaultDuplicateConnectionStrategy
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.defaultDuplicateConnectionStrategy = defaultDuplicateConnectionStrategy;
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
                rs.getString("description"),
                null
            )
        );
    }

    public DataSourceRecord create(DataSourceController.UpsertDataSourceRequest body, Long userId) {
        NormalizedSourceInput input = normalizeInput(body);
        validateSourceNameUnique(input.sourceName(), null);
        String warningMessage = evaluateDuplicateConnectionStrategy(input, null);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                    """
                        insert into data_source(source_name,source_type,host,port,db_name,username,password,status,description,create_user,create_time,update_time)
                        values(?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, input.sourceName());
                statement.setString(2, input.sourceType());
                statement.setString(3, input.host());
                statement.setObject(4, input.port());
                statement.setString(5, input.dbName());
                statement.setString(6, input.username());
                statement.setString(7, input.password());
                statement.setString(8, "ENABLED");
                statement.setString(9, input.description());
                statement.setLong(10, userId);
                statement.setTimestamp(11, now());
                statement.setTimestamp(12, now());
                return statement;
            }, keyHolder);
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicateException(exception);
        }
        return withWarning(get(GeneratedKeyUtils.getLongId(keyHolder, "source_id")), warningMessage);
    }

    public DataSourceRecord update(Long sourceId, DataSourceController.UpsertDataSourceRequest body) {
        get(sourceId);
        NormalizedSourceInput input = normalizeInput(body);
        validateSourceNameUnique(input.sourceName(), sourceId);
        String warningMessage = evaluateDuplicateConnectionStrategy(input, sourceId);
        try {
            jdbcTemplate.update(
                """
                    update data_source
                    set source_name=?,source_type=?,host=?,port=?,db_name=?,username=?,
                        password=coalesce(nullif(?, ''), password),
                        description=?,update_time=?
                    where source_id=?
                """,
                input.sourceName(),
                input.sourceType(),
                input.host(),
                input.port(),
                input.dbName(),
                input.username(),
                input.password(),
                input.description(),
                now(),
                sourceId
            );
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicateException(exception);
        }
        return withWarning(get(sourceId), warningMessage);
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
            throw new IllegalArgumentException("连接测试失败：请检查主机地址、端口及账号密码是否正确", exception);
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
                    rs.getString("description"),
                    null
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

    private NormalizedSourceInput normalizeInput(DataSourceController.UpsertDataSourceRequest body) {
        String sourceName = requireNormalized(body.sourceName(), "数据源名称");
        String sourceType = requireNormalized(body.sourceType(), "数据源类型").toUpperCase(Locale.ROOT);
        if (!List.of("FILE", "MYSQL").contains(sourceType)) {
            throw new IllegalArgumentException("数据源类型仅支持 FILE 或 MYSQL");
        }
        return new NormalizedSourceInput(
            sourceName,
            sourceType,
            normalizeNullableText(body.host()),
            body.port(),
            normalizeNullableText(body.dbName()),
            normalizeNullableText(body.username()),
            body.password(),
            normalizeNullableText(body.description()),
            normalizeDuplicateConnectionStrategy(body.duplicateConnectionStrategy())
        );
    }

    private void validateSourceNameUnique(String sourceName, Long excludeSourceId) {
        String sql = """
            select count(*)
            from data_source
            where lower(trim(source_name)) = lower(?)
        """;
        Integer count;
        if (excludeSourceId == null) {
            count = jdbcTemplate.queryForObject(sql, Integer.class, sourceName);
        } else {
            count = jdbcTemplate.queryForObject(sql + " and source_id <> ?", Integer.class, sourceName, excludeSourceId);
        }
        if (count != null && count > 0) {
            throw new IllegalArgumentException("数据源名称已存在，请更换名称后再保存");
        }
    }

    private String evaluateDuplicateConnectionStrategy(NormalizedSourceInput input, Long excludeSourceId) {
        if (!"MYSQL".equals(input.sourceType())) {
            return null;
        }
        DataSourceRecord duplicated = list().stream()
            .filter(item -> !Objects.equals(item.sourceId(), excludeSourceId))
            .filter(item -> "MYSQL".equalsIgnoreCase(item.sourceType()))
            .filter(item -> sameMysqlConnection(item, input))
            .findFirst()
            .orElse(null);
        if (duplicated == null) {
            return null;
        }
        return switch (input.duplicateConnectionStrategy()) {
            case "ALLOW" -> null;
            case "WARN" -> "检测到与数据源「" + duplicated.sourceName() + "」使用相同的 MYSQL 连接配置，系统已按当前策略允许保存；如无特殊用途，建议复用现有数据源。";
            case "REJECT" -> throw new IllegalArgumentException("已存在使用相同 MYSQL 连接配置的数据源：「" + duplicated.sourceName() + "」，当前策略禁止重复登记");
            default -> throw new IllegalArgumentException("重复连接处理策略仅支持 ALLOW、WARN 或 REJECT");
        };
    }

    private boolean sameMysqlConnection(DataSourceRecord existing, NormalizedSourceInput input) {
        return normalizeMysqlHost(existing.host()).equals(normalizeMysqlHost(input.host()))
            && normalizeMysqlPort(existing.port()).equals(normalizeMysqlPort(input.port()))
            && normalizeConnectionPart(existing.dbName()).equals(normalizeConnectionPart(input.dbName()))
            && normalizeConnectionPart(existing.username()).equals(normalizeConnectionPart(input.username()));
    }

    private DataSourceRecord withWarning(DataSourceRecord record, String warningMessage) {
        return new DataSourceRecord(
            record.sourceId(),
            record.sourceName(),
            record.sourceType(),
            record.host(),
            record.port(),
            record.dbName(),
            record.username(),
            record.status(),
            record.description(),
            warningMessage
        );
    }

    private IllegalArgumentException translateDuplicateException(DataIntegrityViolationException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("source_name")) {
            return new IllegalArgumentException("数据源名称已存在，请更换名称后再保存", exception);
        }
        return new IllegalArgumentException("数据源保存失败，请检查名称或连接配置是否重复", exception);
    }

    private String requireNormalized(String value, String fieldLabel) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldLabel + "不能为空");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeDuplicateConnectionStrategy(String strategy) {
        String candidate = normalizeNullableText(strategy);
        if (candidate == null) {
            candidate = defaultDuplicateConnectionStrategy;
        }
        String normalized = candidate.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALLOW", "WARN", "REJECT").contains(normalized)) {
            throw new IllegalArgumentException("重复连接处理策略仅支持 ALLOW、WARN 或 REJECT");
        }
        return normalized;
    }

    private String normalizeMysqlHost(String host) {
        String normalized = normalizeNullableText(host);
        return normalized == null ? "127.0.0.1" : normalized.toLowerCase(Locale.ROOT);
    }

    private Integer normalizeMysqlPort(Integer port) {
        return port == null ? 3306 : port;
    }

    private String normalizeConnectionPart(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
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
        String description,
        String warningMessage
    ) {
    }

    public record NormalizedSourceInput(
        String sourceName,
        String sourceType,
        String host,
        Integer port,
        String dbName,
        String username,
        String password,
        String description,
        String duplicateConnectionStrategy
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
