package com.datalake.platform.governance;

import com.datalake.platform.common.util.SqlNameUtils;
import com.datalake.platform.dataset.DatasetService;
import com.datalake.platform.dataset.DatasetTableService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class GovernanceService {

    private final JdbcTemplate jdbcTemplate;
    private final DatasetService datasetService;
    private final DatasetTableService datasetTableService;
    private final ObjectMapper objectMapper;

    public GovernanceService(
        JdbcTemplate jdbcTemplate,
        DatasetService datasetService,
        DatasetTableService datasetTableService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasetService = datasetService;
        this.datasetTableService = datasetTableService;
        this.objectMapper = objectMapper;
    }

    public List<OperatorDefinition> listOperators() {
        return jdbcTemplate.query(
            """
                select operator_id,operator_name,operator_key,operator_type,config_schema,description,status
                from operator_def order by operator_id
            """,
            (rs, rowNum) -> new OperatorDefinition(
                rs.getLong("operator_id"),
                rs.getString("operator_name"),
                rs.getString("operator_key"),
                rs.getString("operator_type"),
                rs.getString("config_schema"),
                rs.getString("description"),
                rs.getString("status")
            )
        );
    }

    public List<GovernanceFlowSummary> listFlows() {
        return jdbcTemplate.query(
            """
                select flow_id,flow_name,input_dataset_id,output_dataset_id,create_time
                from governance_flow order by flow_id desc
            """,
            (rs, rowNum) -> new GovernanceFlowSummary(
                rs.getLong("flow_id"),
                rs.getString("flow_name"),
                rs.getLong("input_dataset_id"),
                rs.getObject("output_dataset_id") == null ? null : rs.getLong("output_dataset_id"),
                rs.getTimestamp("create_time").toLocalDateTime().toString()
            )
        );
    }

    public GovernanceFlowSummary saveFlow(String flowName, Long datasetId, List<OperatorStepPayload> operatorChain, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into governance_flow(flow_name,input_dataset_id,operator_chain,creator,create_time,update_time)
                    values(?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, flowName);
            statement.setLong(2, datasetId);
            statement.setString(3, toJson(operatorChain));
            statement.setLong(4, userId);
            statement.setTimestamp(5, now());
            statement.setTimestamp(6, now());
            return statement;
        }, keyHolder);
        return new GovernanceFlowSummary(keyHolder.getKey().longValue(), flowName, datasetId, null, LocalDateTime.now().toString());
    }

    public GovernanceExecutionResult execute(Long datasetId, List<OperatorStepPayload> operatorChain, Long userId) {
        return executeInternal(datasetId, operatorChain, userId, null, true);
    }

    public GovernanceExecutionResult executeSavedFlow(Long flowId, Long userId) {
        FlowDetail flow = getFlow(flowId);
        return executeInternal(flow.inputDatasetId(), flow.operatorChain(), userId, flowId, true);
    }

    public GovernanceExecutionResult executeSavedFlow(Long flowId, Long userId, Long taskId) {
        FlowDetail flow = getFlow(flowId);
        return executeInternal(flow.inputDatasetId(), flow.operatorChain(), userId, flowId, false);
    }

    private GovernanceExecutionResult executeInternal(
        Long datasetId,
        List<OperatorStepPayload> operatorChain,
        Long userId,
        Long flowId,
        boolean writeStandaloneLog
    ) {
        DatasetService.DatasetDetail dataset = datasetService.detail(datasetId);
        List<DatasetService.MetaFieldRecord> metadata = datasetService.metadata(datasetId);
        List<Map<String, Object>> workingRows = deepCopy(datasetTableService.fetchAll(dataset.physicalTableName(), metadata));
        for (OperatorStepPayload operator : operatorChain) {
            workingRows = switch (operator.operatorKey()) {
                case "NULL_FILL" -> applyNullFill(workingRows, operator.params());
                case "DEDUPLICATE" -> applyDeduplicate(workingRows, operator.params());
                case "FIELD_CONVERT" -> applyFieldConvert(workingRows, operator.params());
                case "FILTER_KEEP" -> applyFilterKeep(workingRows, operator.params());
                default -> throw new IllegalArgumentException("未知算子: " + operator.operatorKey());
            };
        }
        List<DatasetService.MetaFieldRecord> outputColumns = inferColumnsFromRows(workingRows);
        String outputName = dataset.datasetName() + "_governed_" + System.currentTimeMillis();
        DatasetService.CreatedDataset outputDataset = datasetService.createDatasetFromRows(
            dataset.sourceId(),
            outputName,
            dataset.formatType(),
            dataset.storagePath(),
            "治理结果数据集",
            userId,
            outputColumns,
            workingRows
        );
        if (flowId != null) {
            jdbcTemplate.update("update governance_flow set output_dataset_id = ?, update_time = ? where flow_id = ?", outputDataset.datasetId(), now(), flowId);
        }
        Long logId = null;
        if (writeStandaloneLog) {
            logId = insertStandaloneLog("GOVERNANCE", flowId == null ? datasetId : flowId, userId, "治理完成，生成数据集 " + outputDataset.datasetName(), null);
        }
        return new GovernanceExecutionResult(datasetId, outputDataset.datasetId(), outputDataset.datasetName(), operatorChain.size(), logId, "治理完成");
    }

    private FlowDetail getFlow(Long flowId) {
        return jdbcTemplate.query(
            "select flow_id,flow_name,input_dataset_id,operator_chain from governance_flow where flow_id = ?",
            rs -> rs.next()
                ? new FlowDetail(
                    rs.getLong("flow_id"),
                    rs.getString("flow_name"),
                    rs.getLong("input_dataset_id"),
                    fromJson(rs.getString("operator_chain"))
                )
                : null,
            flowId
        );
    }

    private List<Map<String, Object>> applyNullFill(List<Map<String, Object>> rows, Map<String, Object> params) {
        String field = String.valueOf(params.get("field"));
        Object value = params.get("value");
        for (Map<String, Object> row : rows) {
            Object current = row.get(field);
            if (current == null || current.toString().isBlank()) {
                row.put(field, value);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> applyDeduplicate(List<Map<String, Object>> rows, Map<String, Object> params) {
        List<String> fields = new ArrayList<>();
        Object fieldsObj = params.get("fields");
        if (fieldsObj instanceof List<?> list) {
            list.forEach(item -> fields.add(String.valueOf(item)));
        } else if (params.get("field") != null) {
            fields.add(String.valueOf(params.get("field")));
        }
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = fields.stream().map(field -> String.valueOf(row.get(field))).collect(Collectors.joining("|"));
            unique.putIfAbsent(key, row);
        }
        return new ArrayList<>(unique.values());
    }

    private List<Map<String, Object>> applyFieldConvert(List<Map<String, Object>> rows, Map<String, Object> params) {
        String sourceField = String.valueOf(params.get("sourceField"));
        String targetField = String.valueOf(params.get("targetField"));
        if (targetField == null || targetField.isBlank() || "null".equals(targetField)) {
            return rows;
        }
        for (Map<String, Object> row : rows) {
            Object value = row.remove(sourceField);
            row.put(targetField, value);
        }
        return rows;
    }

    private List<Map<String, Object>> applyFilterKeep(List<Map<String, Object>> rows, Map<String, Object> params) {
        String field = String.valueOf(params.get("field"));
        String operator = String.valueOf(params.getOrDefault("operator", "EQ"));
        String value = String.valueOf(params.get("value"));
        return rows.stream()
            .filter(row -> match(row.get(field), operator, value))
            .collect(Collectors.toList());
    }

    private boolean match(Object actual, String operator, String expected) {
        if (actual == null) {
            return false;
        }
        return switch (operator.toUpperCase()) {
            case "EQ" -> actual.toString().equals(expected);
            case "LIKE" -> actual.toString().contains(expected);
            case "GT" -> Double.parseDouble(actual.toString()) > Double.parseDouble(expected);
            case "LT" -> Double.parseDouble(actual.toString()) < Double.parseDouble(expected);
            default -> false;
        };
    }

    private List<Map<String, Object>> deepCopy(List<Map<String, Object>> rows) {
        return rows.stream().map(LinkedHashMap::new).collect(Collectors.toList());
    }

    private List<DatasetService.MetaFieldRecord> inferColumnsFromRows(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("治理后数据为空，无法生成新数据集");
        }
        Set<String> usedNames = SqlNameUtils.newNameSet();
        List<String> fields = new ArrayList<>(rows.get(0).keySet());
        List<DatasetService.MetaFieldRecord> columns = new ArrayList<>();
        for (int i = 0; i < fields.size(); i += 1) {
            String fieldName = fields.get(i);
            List<Object> values = rows.stream().map(row -> row.get(fieldName)).toList();
            String physical = SqlNameUtils.ensureUnique(SqlNameUtils.sanitizeColumnName(fieldName), usedNames);
            columns.add(new DatasetService.MetaFieldRecord(null, null, fieldName, physical, inferType(values), true, sample(values), i));
        }
        return columns;
    }

    private String inferType(List<Object> values) {
        List<String> valid = values.stream().filter(value -> value != null && !value.toString().isBlank()).map(Object::toString).toList();
        if (valid.isEmpty()) return "VARCHAR";
        if (valid.stream().allMatch(value -> value.matches("^-?\\d+$"))) return "BIGINT";
        if (valid.stream().allMatch(value -> value.matches("^-?\\d+(\\.\\d+)?$"))) return "DOUBLE";
        if (valid.stream().allMatch(value -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) return "BOOLEAN";
        return "VARCHAR";
    }

    private String sample(List<Object> values) {
        return values.stream().filter(value -> value != null && !value.toString().isBlank()).findFirst().map(Object::toString).orElse("");
    }

    private Long insertStandaloneLog(String taskType, Long targetId, Long userId, String summary, String errorMessage) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    insert into task_log(task_id,task_type,target_id,start_time,end_time,status,execution_summary,error_message,duration,operator_user,create_time)
                    values(?,?,?,?,?,?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setObject(1, null);
            statement.setString(2, taskType);
            statement.setLong(3, targetId);
            statement.setTimestamp(4, now());
            statement.setTimestamp(5, now());
            statement.setString(6, errorMessage == null ? "SUCCESS" : "FAILED");
            statement.setString(7, summary);
            statement.setString(8, errorMessage);
            statement.setLong(9, 0);
            statement.setLong(10, userId);
            statement.setTimestamp(11, now());
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private List<OperatorStepPayload> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException("治理流程解析失败: " + exception.getMessage(), exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("治理流程序列化失败: " + exception.getMessage(), exception);
        }
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private record FlowDetail(Long flowId, String flowName, Long inputDatasetId, List<OperatorStepPayload> operatorChain) {
    }

    public record OperatorDefinition(
        Long operatorId,
        String operatorName,
        String operatorKey,
        String operatorType,
        String configSchema,
        String description,
        String status
    ) {
    }

    public record GovernanceFlowSummary(Long flowId, String flowName, Long inputDatasetId, Long outputDatasetId, String createTime) {
    }

    public record OperatorStepPayload(String operatorKey, Map<String, Object> params) {
    }

    public record GovernanceExecutionResult(
        Long inputDatasetId,
        Long outputDatasetId,
        String outputDatasetName,
        int operatorCount,
        Long logId,
        String summary
    ) {
    }
}

