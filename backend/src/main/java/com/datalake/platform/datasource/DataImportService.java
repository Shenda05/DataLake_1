package com.datalake.platform.datasource;

import com.datalake.platform.dataset.DatasetService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DataImportService {

    private final JdbcTemplate jdbcTemplate;
    private final FileParserService fileParserService;
    private final DatasetService datasetService;
    private final Path storageRoot;

    public DataImportService(
        JdbcTemplate jdbcTemplate,
        FileParserService fileParserService,
        DatasetService datasetService,
        @Value("${app.storage.root}") String storageRoot
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileParserService = fileParserService;
        this.datasetService = datasetService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public ImportResult importFile(MultipartFile file, String datasetName, Long sourceId, Long userId) throws IOException {
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

        KeyHolder keyHolder = new GeneratedKeyHolder();
        FileParserService.ParsedFile parsedFile;
        try {
            parsedFile = fileParserService.parse(file);
            Long importId = insertImportRecord(sourceId, datasetName, parsedFile.formatType(), originalFilename, target, "SUCCESS", parsedFile.rows().size(), null, userId, keyHolder);
            DatasetService.CreatedDataset dataset = datasetService.createImportedDataset(
                sourceId,
                datasetName,
                parsedFile.formatType(),
                target.toString(),
                "导入生成的数据集",
                userId,
                parsedFile.columns(),
                parsedFile.rows()
            );
            return new ImportResult(importId, dataset.datasetId(), dataset.datasetName(), parsedFile.formatType(), dataset.recordCount(), "SUCCESS", "");
        } catch (Exception exception) {
            Long importId = insertImportRecord(sourceId, datasetName, detectFormat(originalFilename), originalFilename, target, "FAILED", 0, exception.getMessage(), userId, keyHolder);
            throw new IllegalArgumentException("导入失败: " + exception.getMessage(), exception);
        }
    }

    public ImportResult replayImport(Long importId, Long userId) {
        ImportDetail detail = detail(importId);
        try {
            FileParserService.ParsedFile parsedFile = fileParserService.parse(Path.of(detail.filePath()), detail.originalFileName(), detail.formatType());
            String datasetName = detail.datasetName() + "_replay_" + System.currentTimeMillis();
            KeyHolder keyHolder = new GeneratedKeyHolder();
            Long newImportId = insertImportRecord(detail.sourceId(), datasetName, detail.formatType(), detail.originalFileName(), Path.of(detail.filePath()), "SUCCESS", parsedFile.rows().size(), null, userId, keyHolder);
            DatasetService.CreatedDataset dataset = datasetService.createImportedDataset(
                detail.sourceId(),
                datasetName,
                detail.formatType(),
                detail.filePath(),
                "由导入模板重放生成",
                userId,
                parsedFile.columns(),
                parsedFile.rows()
            );
            return new ImportResult(newImportId, dataset.datasetId(), dataset.datasetName(), detail.formatType(), dataset.recordCount(), "SUCCESS", "");
        } catch (Exception exception) {
            throw new IllegalArgumentException("重放导入失败: " + exception.getMessage(), exception);
        }
    }

    public List<ImportHistoryItem> history() {
        return jdbcTemplate.query(
            """
                select import_id,source_id,dataset_name,format_type,status,record_count,error_message,create_time
                from import_record
                order by import_id desc
            """,
            (rs, rowNum) -> new ImportHistoryItem(
                rs.getLong("import_id"),
                rs.getLong("source_id"),
                rs.getString("dataset_name"),
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
                select import_id,source_id,dataset_name,format_type,original_file_name,file_path,status,record_count,error_message,create_user,create_time
                from import_record where import_id = ?
            """,
            rs -> rs.next()
                ? new ImportDetail(
                    rs.getLong("import_id"),
                    rs.getLong("source_id"),
                    rs.getString("dataset_name"),
                    rs.getString("format_type"),
                    rs.getString("original_file_name"),
                    rs.getString("file_path"),
                    rs.getString("status"),
                    rs.getLong("record_count"),
                    rs.getString("error_message"),
                    rs.getLong("create_user"),
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

    private Long insertImportRecord(
        Long sourceId,
        String datasetName,
        String formatType,
        String originalFilename,
        Path filePath,
        String status,
        long recordCount,
        String errorMessage,
        Long userId,
        KeyHolder keyHolder
    ) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into import_record(source_id,dataset_name,format_type,original_file_name,file_path,status,record_count,error_message,create_user,create_time)
                    values(?,?,?,?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            if (sourceId == null) {
                statement.setObject(1, null);
            } else {
                statement.setLong(1, sourceId);
            }
            statement.setString(2, datasetName);
            statement.setString(3, formatType);
            statement.setString(4, originalFilename);
            statement.setString(5, filePath.toString());
            statement.setString(6, status);
            statement.setLong(7, recordCount);
            statement.setString(8, errorMessage);
            statement.setLong(9, userId);
            statement.setTimestamp(10, Timestamp.from(java.time.Instant.now()));
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private String detectFormat(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) return "CSV";
        if (lower.endsWith(".json")) return "JSON";
        return "EXCEL";
    }

    public record ImportResult(
        Long importId,
        Long datasetId,
        String datasetName,
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
}
