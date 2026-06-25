package com.datalake.platform.datasource;

import com.datalake.platform.common.domain.BusinessDomainCatalog;
import com.datalake.platform.common.util.GeneratedKeyUtils;
import com.datalake.platform.common.util.SqlNameUtils;
import com.datalake.platform.dataset.DatasetService;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    private static final String FILE_IMPORT_PREFIX = "fileimport://";
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

    public ImportResult importFile(
        MultipartFile file,
        String datasetName,
        String businessDomain,
        Long sourceId,
        String encoding,
        boolean headerRow,
        Long userId
    ) throws IOException {
        ensureSourceEnabled(sourceId);
        String normalizedDomain = BusinessDomainCatalog.normalize(businessDomain);
        FileParserService.ParseOptions parseOptions = new FileParserService.ParseOptions(normalizeEncoding(encoding), headerRow);
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
            FileParserService.ParsedFile parsedFile = fileParserService.parse(target, originalFilename, detectFormat(originalFilename), parseOptions);
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
                buildFileImportPath(target, parseOptions),
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
            insertImportRecord(
                sourceId,
                datasetName,
                normalizedDomain,
                detectFormat(originalFilename),
                originalFilename,
                buildFileImportPath(target, parseOptions),
                "FAILED",
                0,
                exception.getMessage(),
                userId
            );
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
        ensureSourceEnabled(sourceId);
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
        ensureSourceEnabled(detail.sourceId());
        if (detail.filePath().startsWith(DATABASE_IMPORT_PREFIX)) {
            return rerunDatabaseImport(detail, userId, triggerName);
        }
        FileImportLocation fileImportLocation = parseFileImportLocation(detail.filePath(), detail.originalFileName(), detail.formatType());
        Path path = Path.of(fileImportLocation.path()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("导入源文件不存在: " + path);
        }
        String datasetName = buildRerunDatasetName(detail.datasetName(), triggerName);
        try {
            FileParserService.ParsedFile parsedFile = fileParserService.parse(
                path,
                detail.originalFileName(),
                detail.formatType(),
                fileImportLocation.parseOptions()
            );
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
                buildFileImportPath(path, fileImportLocation.parseOptions()),
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
                buildFileImportPath(path, fileImportLocation.parseOptions()),
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

    public List<String> listDatabaseSchemas(Long sourceId) {
        DataSourceService.DataSourceConnectionInfo profile = dataSourceService.connectionInfo(sourceId);
        try (Connection connection = dataSourceService.openConnection(profile, null)) {
            Set<String> schemas = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            if (profile.dbName() != null && !profile.dbName().isBlank()) {
                schemas.add(validateIdentifier(profile.dbName(), "schemaName"));
            }
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet resultSet = metadata.getCatalogs()) {
                while (resultSet.next()) {
                    String catalog = resultSet.getString("TABLE_CAT");
                    if (catalog != null && !catalog.isBlank() && IDENTIFIER_PATTERN.matcher(catalog).matches()) {
                        schemas.add(catalog);
                    }
                }
            }
            try (ResultSet resultSet = metadata.getSchemas()) {
                while (resultSet.next()) {
                    String schema = resultSet.getString("TABLE_SCHEM");
                    if (schema != null && !schema.isBlank() && IDENTIFIER_PATTERN.matcher(schema).matches()) {
                        schemas.add(schema);
                    }
                }
            }
            if (schemas.isEmpty() && connection.getCatalog() != null && !connection.getCatalog().isBlank()) {
                schemas.add(validateIdentifier(connection.getCatalog(), "schemaName"));
            }
            return schemas.stream().toList();
        } catch (Exception exception) {
            throw new IllegalArgumentException("读取 Schema / Database 列表失败: " + exception.getMessage(), exception);
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
        return history(null, null, null, null);
    }

    public List<ImportHistoryItem> history(String businessDomain, String status, String startTime, String endTime) {
        StringBuilder sql = new StringBuilder(
            """
                select ir.import_id,ir.source_id,ir.dataset_name,ir.business_domain,ir.format_type,ir.status,ir.record_count,ir.error_message,
                       ir.create_user,u.username as operator_name,ir.create_time
                from import_record ir
                left join sys_user u on u.user_id = ir.create_user
                where 1 = 1
            """
        );
        List<Object> args = new ArrayList<>();
        if (businessDomain != null && !businessDomain.isBlank()) {
            sql.append(" and ir.business_domain = ?");
            args.add(BusinessDomainCatalog.normalize(businessDomain));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and ir.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        LocalDateTime start = parseHistoryTime(startTime, false);
        LocalDateTime end = parseHistoryTime(endTime, true);
        if (start != null) {
            sql.append(" and ir.create_time >= ?");
            args.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" and ir.create_time <= ?");
            args.add(Timestamp.valueOf(end));
        }
        sql.append(" order by ir.import_id desc");
        return jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> new ImportHistoryItem(
                rs.getLong("import_id"),
                (Long) rs.getObject("source_id"),
                rs.getString("dataset_name"),
                rs.getString("business_domain"),
                rs.getString("format_type"),
                rs.getString("status"),
                rs.getLong("record_count"),
                rs.getString("error_message"),
                (Long) rs.getObject("create_user"),
                rs.getString("operator_name"),
                rs.getTimestamp("create_time").toLocalDateTime().toString()
            ),
            args.toArray()
        );
    }

    public ImportDetail detail(Long importId) {
        ImportDetail detail = jdbcTemplate.query(
            """
                select ir.import_id,ir.source_id,ir.dataset_name,ir.business_domain,ir.format_type,ir.original_file_name,ir.file_path,
                       ir.status,ir.record_count,ir.error_message,ir.create_user,ir.create_time,
                       u.username as operator_name,ds.source_name,ds.source_type
                from import_record ir
                left join sys_user u on u.user_id = ir.create_user
                left join data_source ds on ds.source_id = ir.source_id
                where ir.import_id = ?
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
                    rs.getString("operator_name"),
                    rs.getString("source_name"),
                    rs.getString("source_type"),
                    rs.getTimestamp("create_time").toLocalDateTime().toString(),
                    buildImportParams(
                        rs.getLong("import_id"),
                        (Long) rs.getObject("source_id"),
                        rs.getString("source_name"),
                        rs.getString("source_type"),
                        rs.getString("dataset_name"),
                        rs.getString("business_domain"),
                        rs.getString("format_type"),
                        rs.getString("original_file_name"),
                        rs.getString("file_path"),
                        rs.getString("status"),
                        rs.getLong("record_count")
                    )
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

    private DataSourceService.DataSourceConnectionInfo ensureSourceEnabled(Long sourceId) {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId 不能为空");
        }
        DataSourceService.DataSourceConnectionInfo source = dataSourceService.connectionInfo(sourceId);
        if (!"ENABLED".equalsIgnoreCase(source.status())) {
            throw new IllegalArgumentException("数据源已停用，禁止导入: " + source.sourceName());
        }
        return source;
    }

    private LocalDateTime parseHistoryTime(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        try {
            if (normalized.length() == 10) {
                LocalDate date = LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            if (normalized.contains(" ") && !normalized.contains("T")) {
                normalized = normalized.replace(" ", "T");
            }
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间格式无效: " + raw + "，请使用 yyyy-MM-dd 或 yyyy-MM-ddTHH:mm:ss");
        }
    }

    private Map<String, Object> buildImportParams(
        Long importId,
        Long sourceId,
        String sourceName,
        String sourceType,
        String datasetName,
        String businessDomain,
        String formatType,
        String originalFileName,
        String filePath,
        String status,
        Long recordCount
    ) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("importId", importId);
        params.put("sourceId", sourceId);
        params.put("sourceName", sourceName);
        params.put("sourceType", sourceType);
        params.put("datasetName", datasetName);
        params.put("businessDomain", businessDomain);
        params.put("formatType", formatType);
        params.put("status", status);
        params.put("recordCount", recordCount);
        params.put("originalFileName", originalFileName);
        if (filePath != null && filePath.startsWith(DATABASE_IMPORT_PREFIX)) {
            params.put("storagePath", filePath);
            params.put("importMode", "DATABASE_TABLE");
            try {
                DatabaseImportPath dbPath = parseDatabaseImportPath(filePath);
                params.put("schemaName", dbPath.schemaName());
                params.put("tableName", dbPath.tableName());
            } catch (Exception exception) {
                params.put("databasePathParseError", exception.getMessage());
            }
        } else if (filePath != null && filePath.startsWith(FILE_IMPORT_PREFIX)) {
            params.put("importMode", "FILE_UPLOAD");
            try {
                FileImportLocation fileImport = parseFileImportLocation(filePath, originalFileName, formatType);
                params.put("storagePath", fileImport.path());
                params.put("encoding", fileImport.parseOptions().encoding());
                params.put("headerRow", fileImport.parseOptions().headerRow());
            } catch (Exception exception) {
                params.put("storagePath", filePath);
                params.put("fileImportParseError", exception.getMessage());
            }
        } else {
            params.put("storagePath", filePath);
            params.put("importMode", "FILE_UPLOAD");
        }
        return params;
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

    private String normalizeEncoding(String encoding) {
        String candidate = encoding == null || encoding.isBlank() ? StandardCharsets.UTF_8.name() : encoding.trim();
        try {
            return Charset.forName(candidate).name();
        } catch (Exception exception) {
            throw new IllegalArgumentException("不支持的编码方式: " + candidate, exception);
        }
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

    private String buildFileImportPath(Path actualPath, FileParserService.ParseOptions parseOptions) {
        return FILE_IMPORT_PREFIX
            + URLEncoder.encode(parseOptions.encoding(), StandardCharsets.UTF_8)
            + "/"
            + parseOptions.headerRow()
            + "/"
            + URLEncoder.encode(actualPath.toAbsolutePath().normalize().toString(), StandardCharsets.UTF_8);
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

    private FileImportLocation parseFileImportLocation(String filePath, String originalFileName, String formatType) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("文件导入路径为空");
        }
        if (!filePath.startsWith(FILE_IMPORT_PREFIX)) {
            return new FileImportLocation(filePath, new FileParserService.ParseOptions(StandardCharsets.UTF_8.name(), true));
        }
        String raw = filePath.substring(FILE_IMPORT_PREFIX.length());
        String[] parts = raw.split("/", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("文件导入路径格式错误: " + filePath);
        }
        String encoding = normalizeEncoding(URLDecoder.decode(parts[0], StandardCharsets.UTF_8));
        boolean headerRow = Boolean.parseBoolean(parts[1]);
        String actualPath = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);
        boolean supportsHeaderRow = supportsHeaderRow(formatType, originalFileName);
        return new FileImportLocation(actualPath, new FileParserService.ParseOptions(encoding, supportsHeaderRow ? headerRow : true));
    }

    private String buildOriginalTableName(String schemaName, String tableName) {
        return (schemaName == null || schemaName.isBlank() ? tableName : schemaName + "." + tableName);
    }

    private boolean supportsHeaderRow(String formatType, String originalFileName) {
        String resolvedFormat = formatType;
        if (resolvedFormat == null || resolvedFormat.isBlank()) {
            resolvedFormat = detectFormat(originalFileName == null ? "unknown.csv" : originalFileName);
        }
        String normalized = resolvedFormat.trim().toUpperCase(Locale.ROOT);
        return "CSV".equals(normalized) || "EXCEL".equals(normalized) || "XLS".equals(normalized) || "XLSX".equals(normalized);
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
        Long createUser,
        String operatorName,
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
        String operatorName,
        String sourceName,
        String sourceType,
        String createTime,
        Map<String, Object> importParams
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

    private record FileImportLocation(String path, FileParserService.ParseOptions parseOptions) {
    }

    private record DatabaseTableSnapshot(
        String schemaName,
        String tableName,
        List<DatasetService.MetaFieldRecord> columns,
        List<Map<String, Object>> rows
    ) {
    }
}
