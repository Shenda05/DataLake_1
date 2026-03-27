package com.datalake.platform.datasource;

import com.datalake.platform.common.domain.BusinessDomainCatalog;
import com.datalake.platform.common.util.GeneratedKeyUtils;
import com.datalake.platform.common.util.SqlNameUtils;
import com.datalake.platform.dataset.DatasetService;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DataImportService {

    private static final String DATABASE_IMPORT_PREFIX = "dbimport://";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private final JdbcTemplate jdbcTemplate;
    private final FileParserService fileParserService;
    private final DatasetService datasetService;
    private final DataSourceService dataSourceService;
    private final Path storageRoot;

    public DataImportService(
        JdbcTemplate jdbcTemplate,
        FileParserService fileParserService,
        DatasetService datasetService,
        DataSourceService dataSourceService,
        @Value("${app.storage.root}") String storageRoot
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileParserService = fileParserService;
        this.datasetService = datasetService;
        this.dataSourceService = dataSourceService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public ImportResult importFile(MultipartFile file, String datasetName, String businessDomain, Long sourceId, Long userId) throws IOException {
        String normalizedDomain = BusinessDomainCatalog.normalize(businessDomain);
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        Files.createDirectories(storageRoot);
        String folder = java.time.LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path folderPath = storageRoot.resolve(folder);
        Files.createDirectories(folderPath);
        Path target = folderPath.resolve(System.currentTimeMillis() + "_" + originalFilename);
        file.transferTo(target);

        try {
            FileParserService.ParsedFile parsedFile = fileParserService.parse(target, originalFilename, detectFormat(originalFilename));
            DatasetService.CreatedDataset dataset = datasetService.createImportedDataset(
                sourceId,
                datasetName,
                normalizedDomain,
                parsedFile.formatType(),
                target.toString(),
                "文件导入生成的数据集",
                userId,
                parsedFile.columns(),
                parsedFile.rows()
            );
            Long importId = insertImportRecord(
                sourceId,
                datasetName,
                normalizedDomain,
                parsedFile.formatType(),
                originalFilename,
                target.toString(),
                "SUCCESS",
                parsedFile.rows().size(),
                null,
                userId
            );
            return new ImportResult(
                importId,
                dataset.datasetId(),
                dataset.datasetName(),
                normalizedDomain,
                parsedFile.formatType(),
                dataset.recordCount(),
                "SUCCESS",
                ""
            );
        } catch (Exception exception) {
            insertImportRecord(sourceId, datasetName, normalizedDomain, detectFormat(originalFilename), originalFilename, target.toString(), "FAILED", 0, exception.getMessage(), userId);
            throw new IllegalArgumentException("导入失败: " + exception.getMessage(), exception);
        }
    }

    public ImportResult importDatabaseTable(
        Long sourceId,
        String schemaName,
        String tableName,
        String datasetName,
        String businessDomain,
        String description,
        Long userId
    ) {
        String normalizedDomain = BusinessDomainCatalog.normalize(businessDomain);
        DatabaseTableSnapshot snapshot = readDatabaseTableSnapshot(sourceId, schemaName, tableName, 0);
        String storagePath = buildDatabaseImportPath(sourceId, snapshot.schemaName(), snapshot.tableName());
        String originalName = buildOriginalTableName(snapshot.schemaName(), snapshot.tableName());
        try {
            DatasetService.CreatedDataset dataset = datasetService.createDatasetFromRows(
                sourceId,
                datasetName,
                normalizedDomain,
                "MYSQL_TABLE",
                storagePath,
                description == null || description.isBlank() ? "数据库表导入生成的数据集" : description,
                userId,
                snapshot.columns(),
                snapshot.rows()
            );
            Long importId = insertImportRecord(
                sourceId,
                datasetName,
                normalizedDomain,
                "MYSQL_TABLE",
                originalName,
                storagePath,
                "SUCCESS",
                snapshot.rows().size(),
                null,
                userId
            );
            return new ImportResult(
                importId,
                dataset.datasetId(),
                dataset.datasetName(),
                normalizedDomain,
                "MYSQL_TABLE",
                dataset.recordCount(),
                "SUCCESS",
                ""
            );
        } catch (Exception exception) {
            insertImportRecord(sourceId, datasetName, normalizedDomain, "MYSQL_TABLE", originalName, storagePath, "FAILED", 0, exception.getMessage(), userId);
            throw new IllegalArgumentException("数据库表导入失败: " + exception.getMessage(), exception);
        }
    }

    public ImportResult rerunImport(Long importId, Long userId, String triggerName) {
        ImportDetail detail = detail(importId);
        if (detail.filePath().startsWith(DATABASE_IMPORT_PREFIX)) {
            return rerunDatabaseImport(detail, userId, triggerName);
        }
        Path path = Path.of(detail.filePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("导入源文件不存在: " + path);
        }
        String datasetName = buildRerunDatasetName(detail.datasetName(), triggerName);
        try {
            FileParserService.ParsedFile parsedFile = fileParserService.parse(path, detail.originalFileName(), detail.formatType());
            DatasetService.CreatedDataset dataset = datasetService.createImportedDataset(
                detail.sourceId(),
                datasetName,
                detail.businessDomain(),
                parsedFile.formatType(),
                path.toString(),
                "任务调度重新导入的数据集",
                userId,
                parsedFile.columns(),
                parsedFile.rows()
            );
            Long newImportId = insertImportRecord(
                detail.sourceId(),
                datasetName,
                detail.businessDomain(),
                parsedFile.formatType(),
                detail.originalFileName(),
                path.toString(),
                "SUCCESS",
                parsedFile.rows().size(),
                null,
                userId
            );
            return new ImportResult(
                newImportId,
                dataset.datasetId(),
                dataset.datasetName(),
                detail.businessDomain(),
                parsedFile.formatType(),
                dataset.recordCount(),
                "SUCCESS",
                ""
            );
        } catch (Exception exception) {
            insertImportRecord(
                detail.sourceId(),
                datasetName,
                detail.businessDomain(),
                detail.formatType(),
                detail.originalFileName(),
                path.toString(),
                "FAILED",
                0,
                exception.getMessage(),
                userId
            );
            throw new IllegalArgumentException("任务导入失败: " + exception.getMessage(), exception);
        }
    }

    public List<DatabaseTableSummary> listDatabaseTables(Long sourceId, String schemaName) {
        DataSourceService.DataSourceConnectionInfo profile = dataSourceService.connectionInfo(sourceId);
        String effectiveSchema = normalizeSchemaName(schemaName, profile.dbName());
        try (Connection connection = dataSourceService.openConnection(profile, effectiveSchema)) {
            DatabaseMetaData metadata = connection.getMetaData();
            List<DatabaseTableSummary> tables = new ArrayList<>();
            try (ResultSet resultSet = metadata.getTables(effectiveSchema, null, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    String table = resultSet.getString("TABLE_NAME");
                    tables.add(new DatabaseTableSummary(effectiveSchema, table, buildOriginalTableName(effectiveSchema, table)));
                }
            }
            if (tables.isEmpty()) {
                try (ResultSet resultSet = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                    while (resultSet.next()) {
                        String table = resultSet.getString("TABLE_NAME");
                        tables.add(new DatabaseTableSummary(effectiveSchema, table, buildOriginalTableName(effectiveSchema, table)));
                    }
                }
            }
            return tables.stream().sorted((left, right) -> left.tableName().compareToIgnoreCase(right.tableName())).toList();
        } catch (Exception exception) {
            throw new IllegalArgumentException("读取数据库表列表失败: " + exception.getMessage(), exception);
        }
    }

    public DatabaseTablePreview previewDatabaseTable(Long sourceId, String schemaName, String tableName, Integer limit) {
        DatabaseTableSnapshot snapshot = readDatabaseTableSnapshot(sourceId, schemaName, tableName, limit == null ? 10 : Math.max(1, Math.min(limit, 50)));
        return new DatabaseTablePreview(
            snapshot.schemaName(),
            snapshot.tableName(),
            snapshot.columns().stream()
                .map(column -> new DatabaseColumnPreview(column.fieldName(), column.fieldType(), column.nullable(), column.sampleValue()))
                .toList(),
            snapshot.rows()
        );
    }

    public List<ImportHistoryItem> history() {
        return jdbcTemplate.query(
            """
                select import_id,source_id,dataset_name,business_domain,format_type,status,record_count,error_message,create_time
                from import_record
                order by import_id desc
            """,
            (rs, rowNum) -> new ImportHistoryItem(
                rs.getLong("import_id"),
                (Long) rs.getObject("source_id"),
                rs.getString("dataset_name"),
                rs.getString("business_domain"),
                rs.getString("format_type"),
                rs.getString("status"),
                rs.getLong("record_count"),
                rs.getString("error_message"),
                rs.getTimestamp("create_time").toLocalDateTime().toString()
            )
        );
    }

    public ImportDetail detail(Long importId) {
        ImportDetail detail = jdbcTemplate.query(
            """
                select import_id,source_id,dataset_name,business_domain,format_type,original_file_name,file_path,status,record_count,error_message,create_user,create_time
                from import_record
                where import_id = ?
            """,
            rs -> rs.next()
                ? new ImportDetail(
                    rs.getLong("import_id"),
                    (Long) rs.getObject("source_id"),
                    rs.getString("dataset_name"),
                    rs.getString("business_domain"),
                    rs.getString("format_type"),
                    rs.getString("original_file_name"),
                    rs.getString("file_path"),
                    rs.getString("status"),
                    rs.getLong("record_count"),
                    rs.getString("error_message"),
                    (Long) rs.getObject("create_user"),
                    rs.getTimestamp("create_time").toLocalDateTime().toString()
                )
                : null,
            importId
        );
        if (detail == null) {
            throw new IllegalArgumentException("导入记录不存在: " + importId);
        }
        return detail;
    }

    private ImportResult rerunDatabaseImport(ImportDetail detail, Long userId, String triggerName) {
        DatabaseImportPath location = parseDatabaseImportPath(detail.filePath());
        String datasetName = buildRerunDatasetName(detail.datasetName(), triggerName);
        return importDatabaseTable(
            detail.sourceId(),
            location.schemaName(),
            location.tableName(),
            datasetName,
            detail.businessDomain(),
            "任务调度重新导入的数据集",
            userId
        );
    }

    private DatabaseTableSnapshot readDatabaseTableSnapshot(Long sourceId, String schemaName, String tableName, int limit) {
        DataSourceService.DataSourceConnectionInfo profile = dataSourceService.connectionInfo(sourceId);
        String effectiveSchema = normalizeSchemaName(schemaName, profile.dbName());
        String normalizedTableName = validateIdentifier(tableName, "tableName");
        try (Connection connection = dataSourceService.openConnection(profile, effectiveSchema)) {
            List<DatasetService.MetaFieldRecord> columns = loadColumns(connection, effectiveSchema, normalizedTableName);
            List<Map<String, Object>> rows = loadRows(connection, effectiveSchema, normalizedTableName, columns, limit);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("数据表不存在或没有可识别字段: " + normalizedTableName);
            }
            return new DatabaseTableSnapshot(effectiveSchema, normalizedTableName, columns, rows);
        } catch (Exception exception) {
            throw new IllegalArgumentException("读取数据库表失败: " + exception.getMessage(), exception);
        }
    }

    private List<DatasetService.MetaFieldRecord> loadColumns(Connection connection, String schemaName, String tableName) throws Exception {
        List<DatasetService.MetaFieldRecord> columns = new ArrayList<>();
        try (ResultSet resultSet = connection.getMetaData().getColumns(schemaName, null, tableName, "%")) {
            int order = 0;
            while (resultSet.next()) {
                String fieldName = resultSet.getString("COLUMN_NAME");
                columns.add(new DatasetService.MetaFieldRecord(
                    null,
                    null,
                    fieldName,
                    SqlNameUtils.sanitizeColumnName(fieldName),
                    mapFieldType(resultSet.getInt("DATA_TYPE"), resultSet.getString("TYPE_NAME")),
                    resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                    null,
                    order++
                ));
            }
        }
        if (!columns.isEmpty()) {
            return columns;
        }
        try (PreparedStatement statement = connection.prepareStatement("select * from " + qualifyTable(schemaName, tableName) + " limit 1");
             ResultSet resultSet = statement.executeQuery()) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                String fieldName = metadata.getColumnLabel(index);
                columns.add(new DatasetService.MetaFieldRecord(
                    null,
                    null,
                    fieldName,
                    SqlNameUtils.sanitizeColumnName(fieldName),
                    mapFieldType(metadata.getColumnType(index), metadata.getColumnTypeName(index)),
                    metadata.isNullable(index) != ResultSetMetaData.columnNoNulls,
                    null,
                    index - 1
                ));
            }
        }
        return columns;
    }

    private List<Map<String, Object>> loadRows(
        Connection connection,
        String schemaName,
        String tableName,
        List<DatasetService.MetaFieldRecord> columns,
        int limit
    ) throws Exception {
        String sql = "select * from " + qualifyTable(schemaName, tableName) + " order by 1";
        if (limit > 0) {
            sql += " limit " + limit;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (DatasetService.MetaFieldRecord column : columns) {
                    Object value = resultSet.getObject(column.fieldName());
                    row.put(column.fieldName(), normalizeValue(value));
                }
                rows.add(row);
            }
            if (!rows.isEmpty()) {
                enrichSampleValues(columns, rows.get(0));
            }
            return rows;
        }
    }

    private void enrichSampleValues(List<DatasetService.MetaFieldRecord> columns, Map<String, Object> sampleRow) {
        for (int index = 0; index < columns.size(); index++) {
            DatasetService.MetaFieldRecord column = columns.get(index);
            Object sample = sampleRow.get(column.fieldName());
            columns.set(index, new DatasetService.MetaFieldRecord(
                column.fieldId(),
                column.datasetId(),
                column.fieldName(),
                column.physicalColumnName(),
                column.fieldType(),
                column.nullable(),
                sample == null ? null : String.valueOf(sample),
                column.fieldOrder()
            ));
        }
    }

    private Object normalizeValue(Object value) {
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        return value;
    }

    private Long insertImportRecord(
        Long sourceId,
        String datasetName,
        String businessDomain,
        String formatType,
        String originalFilename,
        String filePath,
        String status,
        long recordCount,
        String errorMessage,
        Long userId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into import_record(source_id,dataset_name,business_domain,format_type,original_file_name,file_path,status,record_count,error_message,create_user,create_time)
                    values(?,?,?,?,?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setObject(1, sourceId);
            statement.setString(2, datasetName);
            statement.setString(3, BusinessDomainCatalog.normalize(businessDomain));
            statement.setString(4, formatType);
            statement.setString(5, originalFilename);
            statement.setString(6, filePath);
            statement.setString(7, status);
            statement.setLong(8, recordCount);
            statement.setString(9, errorMessage);
            statement.setLong(10, userId);
            statement.setTimestamp(11, Timestamp.from(java.time.Instant.now()));
            return statement;
        }, keyHolder);
        return GeneratedKeyUtils.getLongId(keyHolder, "import_id");
    }

    private String detectFormat(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return "CSV";
        }
        if (lower.endsWith(".json")) {
            return "JSON";
        }
        return "EXCEL";
    }

    private String buildRerunDatasetName(String datasetName, String triggerName) {
        String prefix = triggerName == null || triggerName.isBlank() ? datasetName + "_重新导入" : triggerName + "_导入结果";
        return prefix + "_" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String normalizeSchemaName(String schemaName, String defaultSchema) {
        String effective = schemaName == null || schemaName.isBlank() ? defaultSchema : schemaName.trim();
        if (effective == null || effective.isBlank()) {
            throw new IllegalArgumentException("数据库名称不能为空，请先在数据源中配置 dbName 或导入时显式指定 schemaName");
        }
        return validateIdentifier(effective, "schemaName");
    }

    private String validateIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (!IDENTIFIER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " 仅支持字母、数字和下划线");
        }
        return normalized;
    }

    private String qualifyTable(String schemaName, String tableName) {
        String safeTable = validateIdentifier(tableName, "tableName");
        if (schemaName == null || schemaName.isBlank()) {
            return "`" + safeTable + "`";
        }
        String safeSchema = validateIdentifier(schemaName, "schemaName");
        return "`" + safeSchema + "`.`" + safeTable + "`";
    }

    private String mapFieldType(int sqlType, String typeName) {
        return switch (sqlType) {
            case Types.BIT, Types.BOOLEAN -> "BOOLEAN";
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> "BIGINT";
            case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> "DOUBLE";
            default -> {
                String normalizedTypeName = typeName == null ? "" : typeName.toUpperCase(Locale.ROOT);
                if (normalizedTypeName.contains("INT")) {
                    yield "BIGINT";
                }
                if (normalizedTypeName.contains("DOUBLE") || normalizedTypeName.contains("DECIMAL") || normalizedTypeName.contains("FLOAT")) {
                    yield "DOUBLE";
                }
                if (normalizedTypeName.contains("BOOL")) {
                    yield "BOOLEAN";
                }
                yield "VARCHAR";
            }
        };
    }

    private String buildDatabaseImportPath(Long sourceId, String schemaName, String tableName) {
        return DATABASE_IMPORT_PREFIX
            + sourceId
            + "/"
            + URLEncoder.encode(schemaName, StandardCharsets.UTF_8)
            + "/"
            + URLEncoder.encode(tableName, StandardCharsets.UTF_8);
    }

    private DatabaseImportPath parseDatabaseImportPath(String filePath) {
        String raw = filePath.substring(DATABASE_IMPORT_PREFIX.length());
        String[] parts = raw.split("/", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("数据库导入路径格式错误: " + filePath);
        }
        return new DatabaseImportPath(
            Long.parseLong(parts[0]),
            URLDecoder.decode(parts[1], StandardCharsets.UTF_8),
            URLDecoder.decode(parts[2], StandardCharsets.UTF_8)
        );
    }

    private String buildOriginalTableName(String schemaName, String tableName) {
        return (schemaName == null || schemaName.isBlank() ? tableName : schemaName + "." + tableName);
    }

    public record ImportResult(
        Long importId,
        Long datasetId,
        String datasetName,
        String businessDomain,
        String formatType,
        int recordCount,
        String status,
        String errorMessage
    ) {
    }

    public record ImportHistoryItem(
        Long importId,
        Long sourceId,
        String datasetName,
        String businessDomain,
        String formatType,
        String status,
        Long recordCount,
        String errorMessage,
        String createTime
    ) {
    }

    public record ImportDetail(
        Long importId,
        Long sourceId,
        String datasetName,
        String businessDomain,
        String formatType,
        String originalFileName,
        String filePath,
        String status,
        Long recordCount,
        String errorMessage,
        Long createUser,
        String createTime
    ) {
    }

    public record DatabaseTableSummary(String schemaName, String tableName, String displayName) {
    }

    public record DatabaseColumnPreview(String fieldName, String fieldType, boolean nullable, String sampleValue) {
    }

    public record DatabaseTablePreview(
        String schemaName,
        String tableName,
        List<DatabaseColumnPreview> columns,
        List<Map<String, Object>> records
    ) {
    }

    private record DatabaseImportPath(Long sourceId, String schemaName, String tableName) {
    }

    private record DatabaseTableSnapshot(
        String schemaName,
        String tableName,
        List<DatasetService.MetaFieldRecord> columns,
        List<Map<String, Object>> rows
    ) {
    }
}
