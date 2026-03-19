package com.datalake.platform.dataset;

import com.datalake.platform.common.util.SqlNameUtils;
import com.datalake.platform.common.web.PageResponse;
import com.datalake.platform.datasource.FileParserService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {

    private final JdbcTemplate jdbcTemplate;
    private final DatasetTableService datasetTableService;

    public DatasetService(JdbcTemplate jdbcTemplate, DatasetTableService datasetTableService) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasetTableService = datasetTableService;
    }

    public CreatedDataset createImportedDataset(
        Long sourceId,
        String datasetName,
        String formatType,
        String storagePath,
        String description,
        Long creator,
        List<FileParserService.ParsedColumn> parsedColumns,
        List<Map<String, Object>> rows
    ) {
        List<MetaFieldRecord> columns = parsedColumns.stream()
            .map(column -> new MetaFieldRecord(
                null,
                null,
                column.fieldName(),
                column.physicalColumnName(),
                column.fieldType(),
                column.nullable(),
                column.sampleValue(),
                column.fieldOrder()
            ))
            .toList();
        return createDatasetFromRows(sourceId, datasetName, formatType, storagePath, description, creator, columns, rows);
    }

    public CreatedDataset createDatasetFromRows(
        Long sourceId,
        String datasetName,
        String formatType,
        String storagePath,
        String description,
        Long creator,
        List<MetaFieldRecord> columns,
        List<Map<String, Object>> rows
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into data_set(dataset_name,source_id,format_type,record_count,field_count,storage_path,physical_table_name,description,creator,status,create_time,update_time)
                    values(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, datasetName);
            statement.setObject(2, sourceId);
            statement.setString(3, formatType);
            statement.setLong(4, rows.size());
            statement.setInt(5, columns.size());
            statement.setString(6, storagePath);
            statement.setString(7, "pending_table_name");
            statement.setString(8, description);
            statement.setLong(9, creator);
            statement.setString(10, "READY");
            statement.setTimestamp(11, now());
            statement.setTimestamp(12, now());
            return statement;
        }, keyHolder);
        Long datasetId = keyHolder.getKey().longValue();
        String physicalTableName = SqlNameUtils.sanitizeTableName("dl_dataset_" + datasetId);
        jdbcTemplate.update("update data_set set physical_table_name = ?, update_time = ? where dataset_id = ?", physicalTableName, now(), datasetId);
        List<MetaFieldRecord> persistedColumns = insertMetaFields(datasetId, columns);
        datasetTableService.createPhysicalTable(physicalTableName, persistedColumns);
        datasetTableService.insertRows(physicalTableName, persistedColumns, rows);
        return new CreatedDataset(datasetId, datasetName, physicalTableName, rows.size(), persistedColumns.size());
    }

    public List<DatasetSummary> list() {
        return jdbcTemplate.query(
            """
                select dataset_id,source_id,dataset_name,format_type,record_count,field_count,status,creator,physical_table_name
                from data_set
                order by dataset_id desc
            """,
            (rs, rowNum) -> new DatasetSummary(
                rs.getLong("dataset_id"),
                (Long) rs.getObject("source_id"),
                rs.getString("dataset_name"),
                rs.getString("format_type"),
                rs.getLong("record_count"),
                rs.getInt("field_count"),
                rs.getString("status"),
                (Long) rs.getObject("creator"),
                rs.getString("physical_table_name")
            )
        );
    }

    public DatasetDetail detail(Long datasetId) {
        DatasetDetail detail = jdbcTemplate.query(
            """
                select dataset_id,dataset_name,source_id,format_type,record_count,field_count,status,creator,storage_path,physical_table_name,description,create_time,update_time
                from data_set
                where dataset_id = ?
            """,
            rs -> rs.next()
                ? new DatasetDetail(
                    rs.getLong("dataset_id"),
                    rs.getString("dataset_name"),
                    (Long) rs.getObject("source_id"),
                    rs.getString("format_type"),
                    rs.getLong("record_count"),
                    rs.getInt("field_count"),
                    rs.getString("status"),
                    (Long) rs.getObject("creator"),
                    rs.getString("storage_path"),
                    rs.getString("physical_table_name"),
                    rs.getString("description"),
                    rs.getTimestamp("create_time").toLocalDateTime().toString(),
                    rs.getTimestamp("update_time").toLocalDateTime().toString()
                )
                : null,
            datasetId
        );
        if (detail == null) {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }
        return detail;
    }

    public List<MetaFieldRecord> metadata(Long datasetId) {
        return jdbcTemplate.query(
            """
                select field_id,dataset_id,field_name,physical_column_name,field_type,nullable,sample_value,field_order
                from meta_field
                where dataset_id = ?
                order by field_order asc, field_id asc
            """,
            (rs, rowNum) -> new MetaFieldRecord(
                rs.getLong("field_id"),
                rs.getLong("dataset_id"),
                rs.getString("field_name"),
                rs.getString("physical_column_name"),
                rs.getString("field_type"),
                rs.getBoolean("nullable"),
                rs.getString("sample_value"),
                rs.getInt("field_order")
            ),
            datasetId
        );
    }

    public PageResponse<Map<String, Object>> preview(Long datasetId, int pageNum, int pageSize) {
        DatasetDetail detail = detail(datasetId);
        return datasetTableService.preview(detail.physicalTableName(), metadata(datasetId), pageNum, pageSize);
    }

    public void delete(Long datasetId) {
        DatasetDetail detail = detail(datasetId);
        datasetTableService.dropTable(detail.physicalTableName());
        jdbcTemplate.update("delete from meta_field where dataset_id = ?", datasetId);
        jdbcTemplate.update("delete from data_set where dataset_id = ?", datasetId);
    }

    private List<MetaFieldRecord> insertMetaFields(Long datasetId, List<MetaFieldRecord> columns) {
        List<MetaFieldRecord> persisted = new ArrayList<>();
        for (MetaFieldRecord column : columns) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                    """
                        insert into meta_field(dataset_id,field_name,physical_column_name,field_type,nullable,sample_value,field_order,create_time)
                        values(?,?,?,?,?,?,?,?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, datasetId);
                statement.setString(2, column.fieldName());
                statement.setString(3, column.physicalColumnName());
                statement.setString(4, column.fieldType());
                statement.setBoolean(5, column.nullable());
                statement.setString(6, column.sampleValue());
                statement.setInt(7, column.fieldOrder());
                statement.setTimestamp(8, now());
                return statement;
            }, keyHolder);
            persisted.add(new MetaFieldRecord(
                keyHolder.getKey().longValue(),
                datasetId,
                column.fieldName(),
                column.physicalColumnName(),
                column.fieldType(),
                column.nullable(),
                column.sampleValue(),
                column.fieldOrder()
            ));
        }
        return persisted;
    }

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
    }

    public record CreatedDataset(Long datasetId, String datasetName, String physicalTableName, int recordCount, int fieldCount) {
    }

    public record DatasetSummary(
        Long datasetId,
        Long sourceId,
        String datasetName,
        String formatType,
        Long recordCount,
        Integer fieldCount,
        String status,
        Long creator,
        String physicalTableName
    ) {
    }

    public record DatasetDetail(
        Long datasetId,
        String datasetName,
        Long sourceId,
        String formatType,
        Long recordCount,
        Integer fieldCount,
        String status,
        Long creator,
        String storagePath,
        String physicalTableName,
        String description,
        String createTime,
        String updateTime
    ) {
    }

    public record MetaFieldRecord(
        Long fieldId,
        Long datasetId,
        String fieldName,
        String physicalColumnName,
        String fieldType,
        boolean nullable,
        String sampleValue,
        int fieldOrder
    ) {
    }
}
