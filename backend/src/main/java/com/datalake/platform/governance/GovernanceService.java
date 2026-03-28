package com.datalake.platform.governance;

import com.datalake.platform.common.util.GeneratedKeyUtils;
import com.datalake.platform.dataset.DatasetService;
import com.datalake.platform.dataset.DatasetTableService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<OperatorSummary> operators() {
        return jdbcTemplate.query(
            """
                select operator_id,operator_name,operator_key,operator_type,description,status
                from operator_def
                where status = 'ENABLED'
                order by operator_id asc
            """,
            (rs, rowNum) -> new OperatorSummary(
                rs.getLong("operator_id"),
                rs.getString("operator_name"),
                rs.getString("operator_key"),
                rs.getString("operator_type"),
                rs.getString("description"),
                rs.getString("status")
            )
        );
    }

    public List<GovernanceFlowSummary> flows() {
        return jdbcTemplate.query(
            """
                select f.flow_id,f.flow_name,f.input_dataset_id,input_ds.dataset_name as input_dataset_name,
                       f.output_dataset_id,output_ds.dataset_name as output_dataset_name,f.operator_chain,f.creator,
                       creator_user.username as creator_name,f.create_time,f.update_time
                from governance_flow f
                join data_set input_ds on input_ds.dataset_id = f.input_dataset_id
                left join data_set output_ds on output_ds.dataset_id = f.output_dataset_id
                left join sys_user creator_user on creator_user.user_id = f.creator
                order by f.flow_id desc
            """,
            (rs, rowNum) -> new GovernanceFlowSummary(
                rs.getLong("flow_id"),
                rs.getString("flow_name"),
                rs.getLong("input_dataset_id"),
                rs.getString("input_dataset_name"),
                (Long) rs.getObject("output_dataset_id"),
                rs.getString("output_dataset_name"),
                parseOperatorChain(rs.getString("operator_chain")),
                (Long) rs.getObject("creator"),
                rs.getString("creator_name"),
                rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime().toString(),
                rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime().toString()
            )
        );
    }

    @Transactional
    public SaveFlowResult createFlow(String flowName, Long datasetId, List<OperatorStep> operatorChain, Long userId) {
        datasetService.detail(datasetId);
        validateOperatorChain(operatorChain);
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
            statement.setString(3, writeOperatorChain(operatorChain));
            statement.setLong(4, userId);
            statement.setTimestamp(5, now());
            statement.setTimestamp(6, now());
            return statement;
        }, keyHolder);
        return new SaveFlowResult(GeneratedKeyUtils.getLongId(keyHolder, "flow_id"), flowName, operatorChain.size());
    }

    @Transactional
    public ExecutionResult execute(Long datasetId, List<OperatorStep> operatorChain, Long userId, String executionName) {
        validateOperatorChain(operatorChain);
        DatasetService.DatasetDetail inputDataset = datasetService.detail(datasetId);
        List<DatasetService.MetaFieldRecord> metadata = cloneColumns(datasetService.metadata(datasetId));
        List<Map<String, Object>> rows = deepCopy(datasetTableService.fetchAll(inputDataset.physicalTableName(), metadata, null, null));

        List<Map<String, Object>> currentRows = rows;
        long abnormalHandledCount = 0L;
        for (int index = 0; index < operatorChain.size(); index++) {
            OperatorStep step = operatorChain.get(index);
            try {
                StepApplyResult stepResult = applyStep(metadata, currentRows, step);
                currentRows = stepResult.rows();
                abnormalHandledCount += Math.max(0L, stepResult.handledCount());
            } catch (Exception exception) {
                throw new GovernanceExecutionException(
                    new FailureStep(index + 1, step.operatorKey(), exception.getMessage()),
                    rows.size(),
                    currentRows.size(),
                    abnormalHandledCount,
                    exception
                );
            }
        }

        String outputDatasetName = buildOutputDatasetName(inputDataset.datasetName(), executionName);
        DatasetService.CreatedDataset createdDataset = datasetService.createDatasetFromRows(
            inputDataset.sourceId(),
            outputDatasetName,
            inputDataset.businessDomain(),
            inputDataset.formatType(),
            inputDataset.storagePath(),
            "治理流程输出数据集",
            userId,
            metadata,
            currentRows
        );
        return new ExecutionResult(
            datasetId,
            createdDataset.datasetId(),
            createdDataset.datasetName(),
            operatorChain.size(),
            rows.size(),
            createdDataset.recordCount(),
            abnormalHandledCount,
            "治理完成，已生成新数据集 " + createdDataset.datasetName() + "，记录数 " + createdDataset.recordCount()
        );
    }

    @Transactional
    public ExecutionResult executeSavedFlow(Long flowId, Long userId, String executionName) {
        GovernanceFlowSummary flow = flow(flowId);
        ExecutionResult result = execute(flow.inputDatasetId(), flow.operatorChain(), userId, executionName == null ? flow.flowName() : executionName);
        jdbcTemplate.update(
            "update governance_flow set output_dataset_id = ?, update_time = ? where flow_id = ?",
            result.outputDatasetId(),
            now(),
            flowId
        );
        return result;
    }

    public GovernanceFlowSummary flow(Long flowId) {
        GovernanceFlowSummary flow = flows().stream()
            .filter(item -> Objects.equals(item.flowId(), flowId))
            .findFirst()
            .orElse(null);
        if (flow == null) {
            throw new IllegalArgumentException("治理流程不存在: " + flowId);
        }
        return flow;
    }

    private void validateOperatorChain(List<OperatorStep> operatorChain) {
        if (operatorChain == null || operatorChain.isEmpty()) {
            throw new IllegalArgumentException("治理算子链不能为空");
        }
        List<String> supported = operators().stream().map(OperatorSummary::operatorKey).toList();
        for (OperatorStep step : operatorChain) {
            if (!supported.contains(step.operatorKey())) {
                throw new IllegalArgumentException("不支持的治理算子: " + step.operatorKey());
            }
        }
    }

    private String writeOperatorChain(List<OperatorStep> operatorChain) {
        try {
            return objectMapper.writeValueAsString(operatorChain);
        } catch (Exception exception) {
            throw new IllegalArgumentException("治理算子链序列化失败: " + exception.getMessage(), exception);
        }
    }

    private List<OperatorStep> parseOperatorChain(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<List<OperatorStep>>() {
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException("治理算子链反序列化失败: " + exception.getMessage(), exception);
        }
    }

    private StepApplyResult applyStep(
        List<DatasetService.MetaFieldRecord> metadata,
        List<Map<String, Object>> rows,
        OperatorStep step
    ) {
        return switch (step.operatorKey().toUpperCase(Locale.ROOT)) {
            case "NULL_FILL" -> nullFill(rows, requireField(step), param(step.params(), "fillValue", "UNKNOWN"));
            case "DEDUPLICATE" -> deduplicate(rows, parseFields(step.params()));
            case "ORDER_DEDUP" -> orderDedup(rows, step.params());
            case "FIELD_CONVERT" -> fieldConvert(metadata, rows, requireField(step), param(step.params(), "transform", "TRIM"));
            case "AMOUNT_NORMALIZE" -> amountNormalize(metadata, rows, requireField(step));
            case "TIME_NORMALIZE" -> timeNormalize(rows, requireField(step));
            case "CATEGORY_NORMALIZE" -> categoryNormalize(rows, requireField(step));
            case "STATUS_NORMALIZE" -> statusNormalize(rows, requireField(step));
            case "FILTER_KEEP" -> filterKeep(rows, requireField(step), param(step.params(), "operator", "LIKE"), param(step.params(), "value", param(step.params(), "keyword", "")));
            default -> throw new IllegalArgumentException("不支持的治理算子: " + step.operatorKey());
        };
    }

    private StepApplyResult nullFill(List<Map<String, Object>> rows, String field, String fillValue) {
        List<Map<String, Object>> result = new ArrayList<>();
        long handledCount = 0L;
        for (Map<String, Object> row : rows) {
            Map<String, Object> copied = new LinkedHashMap<>(row);
            Object value = copied.get(field);
            if (value == null || String.valueOf(value).isBlank()) {
                copied.put(field, fillValue);
                handledCount += 1;
            }
            result.add(copied);
        }
        return new StepApplyResult(result, handledCount);
    }

    private StepApplyResult deduplicate(List<Map<String, Object>> rows, List<String> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("去重算子至少需要一个字段");
        }
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = fields.stream().map(field -> String.valueOf(row.get(field))).collect(Collectors.joining("|"));
            unique.putIfAbsent(key, new LinkedHashMap<>(row));
        }
        List<Map<String, Object>> result = new ArrayList<>(unique.values());
        return new StepApplyResult(result, Math.max(0, rows.size() - result.size()));
    }

    private StepApplyResult orderDedup(List<Map<String, Object>> rows, Map<String, Object> params) {
        List<String> fields = parseFields(params);
        if (fields.isEmpty()) {
            for (String candidate : List.of("order_id", "order_no", "id")) {
                if (rows.stream().findFirst().map(row -> row.containsKey(candidate)).orElse(false)) {
                    fields = List.of(candidate);
                    break;
                }
            }
        }
        return deduplicate(rows, fields);
    }

    private StepApplyResult fieldConvert(
        List<DatasetService.MetaFieldRecord> metadata,
        List<Map<String, Object>> rows,
        String field,
        String transform
    ) {
        updateFieldType(metadata, field, transform);
        List<Map<String, Object>> result = new ArrayList<>();
        long handledCount = 0L;
        for (Map<String, Object> row : rows) {
            Map<String, Object> copied = new LinkedHashMap<>(row);
            Object value = copied.get(field);
            if (value == null) {
                result.add(copied);
                continue;
            }
            String text = String.valueOf(value);
            String normalized = transform == null ? "TRIM" : transform.toUpperCase(Locale.ROOT);
            Object converted = switch (normalized) {
                case "UPPER" -> text.toUpperCase(Locale.ROOT);
                case "LOWER" -> text.toLowerCase(Locale.ROOT);
                case "NUMBER" -> parseNumber(text);
                default -> text.trim();
            };
            if (!Objects.equals(value, converted)) {
                handledCount += 1;
            }
            copied.put(field, converted);
            result.add(copied);
        }
        return new StepApplyResult(result, handledCount);
    }

    private StepApplyResult amountNormalize(
        List<DatasetService.MetaFieldRecord> metadata,
        List<Map<String, Object>> rows,
        String field
    ) {
        updateFieldType(metadata, field, "NUMBER");
        List<Map<String, Object>> result = new ArrayList<>();
        long handledCount = 0L;
        for (Map<String, Object> row : rows) {
            Map<String, Object> copied = new LinkedHashMap<>(row);
            Object normalized = normalizeAmountValue(row.get(field));
            if (!Objects.equals(row.get(field), normalized)) {
                handledCount += 1;
            }
            copied.put(field, normalized);
            result.add(copied);
        }
        return new StepApplyResult(result, handledCount);
    }

    private StepApplyResult timeNormalize(List<Map<String, Object>> rows, String field) {
        List<Map<String, Object>> result = new ArrayList<>();
        long handledCount = 0L;
        for (Map<String, Object> row : rows) {
            Map<String, Object> copied = new LinkedHashMap<>(row);
            Object value = row.get(field);
            Object normalized = normalizeTimeValue(value);
            if (!Objects.equals(value, normalized)) {
                handledCount += 1;
            }
            copied.put(field, normalized);
            result.add(copied);
        }
        return new StepApplyResult(result, handledCount);
    }

    private StepApplyResult categoryNormalize(List<Map<String, Object>> rows, String field) {
        List<Map<String, Object>> result = new ArrayList<>();
        long handledCount = 0L;
        for (Map<String, Object> row : rows) {
            Map<String, Object> copied = new LinkedHashMap<>(row);
            Object value = row.get(field);
            Object normalized = normalizeCategoryValue(value);
            if (!Objects.equals(value, normalized)) {
                handledCount += 1;
            }
            copied.put(field, normalized);
            result.add(copied);
        }
        return new StepApplyResult(result, handledCount);
    }

    private StepApplyResult statusNormalize(List<Map<String, Object>> rows, String field) {
        List<Map<String, Object>> result = new ArrayList<>();
        long handledCount = 0L;
        for (Map<String, Object> row : rows) {
            Map<String, Object> copied = new LinkedHashMap<>(row);
            Object value = row.get(field);
            Object normalized = normalizeStatusValue(value);
            if (!Objects.equals(value, normalized)) {
                handledCount += 1;
            }
            copied.put(field, normalized);
            result.add(copied);
        }
        return new StepApplyResult(result, handledCount);
    }

    private StepApplyResult filterKeep(List<Map<String, Object>> rows, String field, String operator, String value) {
        String normalizedOperator = operator == null ? "LIKE" : operator.toUpperCase(Locale.ROOT);
        List<Map<String, Object>> result = rows.stream()
            .filter(row -> matches(row.get(field), normalizedOperator, value))
            .map(row -> (Map<String, Object>) new LinkedHashMap<>(row))
            .collect(Collectors.toCollection(ArrayList::new));
        return new StepApplyResult(result, Math.max(0, rows.size() - result.size()));
    }

    private boolean matches(Object currentValue, String operator, String expectedValue) {
        String left = currentValue == null ? "" : String.valueOf(currentValue);
        String right = expectedValue == null ? "" : expectedValue;
        return switch (operator) {
            case "EQ" -> left.equals(right);
            case "GT" -> compareNumber(left, right) > 0;
            case "LT" -> compareNumber(left, right) < 0;
            default -> left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
        };
    }

    private int compareNumber(String left, String right) {
        return Double.compare(Double.parseDouble(left), Double.parseDouble(right));
    }

    private Object parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.matches("^-?\\d+$")) {
            return Long.parseLong(text);
        }
        return Double.parseDouble(text);
    }

    private Object normalizeAmountValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) {
            return null;
        }
        return Math.round(Double.parseDouble(normalized) * 100.0d) / 100.0d;
    }

    private Object normalizeTimeValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        if (raw instanceof java.time.LocalDate localDate) {
            return localDate.atStartOfDay().toString();
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (raw instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay().toString();
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank()) {
            return null;
        }
        List<java.time.format.DateTimeFormatter> dateTimeFormats = List.of(
            java.time.format.DateTimeFormatter.ISO_DATE_TIME,
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        );
        for (java.time.format.DateTimeFormatter formatter : dateTimeFormats) {
            try {
                return java.time.LocalDateTime.parse(text, formatter).toString();
            } catch (Exception ignored) {
            }
        }
        List<java.time.format.DateTimeFormatter> dateFormats = List.of(
            java.time.format.DateTimeFormatter.ISO_DATE,
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );
        for (java.time.format.DateTimeFormatter formatter : dateFormats) {
            try {
                return java.time.LocalDate.parse(text, formatter).atStartOfDay().toString();
            } catch (Exception ignored) {
            }
        }
        return text;
    }

    private Object normalizeCategoryValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("手机") || lower.contains("phone")) {
            return "3C数码";
        }
        if (lower.contains("食品") || lower.contains("food")) {
            return "食品生鲜";
        }
        if (lower.contains("服装") || lower.contains("clothes") || lower.contains("fashion")) {
            return "服饰鞋包";
        }
        if (lower.contains("家电") || lower.contains("appliance")) {
            return "家用电器";
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private Object normalizeStatusValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (List.of("paid", "success", "已支付", "支付成功", "completed").contains(lower)) {
            return "PAID";
        }
        if (List.of("pending", "待支付", "created", "new").contains(lower)) {
            return "PENDING";
        }
        if (List.of("cancelled", "canceled", "已取消", "closed").contains(lower)) {
            return "CANCELLED";
        }
        if (List.of("refunded", "已退款").contains(lower)) {
            return "REFUNDED";
        }
        if (List.of("failed", "失败").contains(lower)) {
            return "FAILED";
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private void updateFieldType(List<DatasetService.MetaFieldRecord> metadata, String field, String transform) {
        if (!"NUMBER".equalsIgnoreCase(transform)) {
            return;
        }
        for (int i = 0; i < metadata.size(); i++) {
            DatasetService.MetaFieldRecord item = metadata.get(i);
            if (item.fieldName().equals(field)) {
                metadata.set(i, new DatasetService.MetaFieldRecord(
                    item.fieldId(),
                    item.datasetId(),
                    item.fieldName(),
                    item.physicalColumnName(),
                    "DOUBLE",
                    item.nullable(),
                    item.sampleValue(),
                    item.fieldOrder()
                ));
                return;
            }
        }
    }

    private List<Map<String, Object>> deepCopy(List<Map<String, Object>> rows) {
        return rows.stream()
            .map(row -> (Map<String, Object>) new LinkedHashMap<>(row))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<DatasetService.MetaFieldRecord> cloneColumns(List<DatasetService.MetaFieldRecord> metadata) {
        return metadata.stream()
            .map(item -> new DatasetService.MetaFieldRecord(
                item.fieldId(),
                item.datasetId(),
                item.fieldName(),
                item.physicalColumnName(),
                item.fieldType(),
                item.nullable(),
                item.sampleValue(),
                item.fieldOrder()
            ))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> parseFields(Map<String, Object> params) {
        Object fields = params == null ? null : params.get("fields");
        if (fields instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList();
        }
        String singleField = param(params, "field", "");
        if (!singleField.isBlank()) {
            return List.of(singleField);
        }
        String multiField = param(params, "fields", "");
        if (multiField.isBlank()) {
            return List.of();
        }
        return List.of(multiField.split(",")).stream().map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private String requireField(OperatorStep step) {
        String field = param(step.params(), "field", "");
        if (field.isBlank()) {
            throw new IllegalArgumentException("算子 " + step.operatorKey() + " 缺少 field 参数");
        }
        return field;
    }

    private String param(Map<String, Object> params, String key, String defaultValue) {
        if (params == null || params.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(params.get(key));
    }

    private String buildOutputDatasetName(String inputDatasetName, String executionName) {
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String base = executionName == null || executionName.isBlank() ? inputDatasetName + "_治理结果" : executionName;
        return base + "_" + suffix;
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    public record OperatorSummary(
        Long operatorId,
        String operatorName,
        String operatorKey,
        String operatorType,
        String description,
        String status
    ) {
    }

    public record GovernanceFlowSummary(
        Long flowId,
        String flowName,
        Long inputDatasetId,
        String inputDatasetName,
        Long outputDatasetId,
        String outputDatasetName,
        List<OperatorStep> operatorChain,
        Long creator,
        String creatorName,
        String createTime,
        String updateTime
    ) {
    }

    public record OperatorStep(String operatorKey, Map<String, Object> params) {
    }

    public record SaveFlowResult(Long flowId, String flowName, int operatorCount) {
    }

    public record ExecutionResult(
        Long inputDatasetId,
        Long outputDatasetId,
        String outputDatasetName,
        int operatorCount,
        long inputRecordCount,
        long outputRecordCount,
        long abnormalHandledCount,
        String summary
    ) {
    }

    public record FailureStep(Integer stepIndex, String operatorKey, String reason) {
    }

    private record StepApplyResult(List<Map<String, Object>> rows, long handledCount) {
    }

    public static class GovernanceExecutionException extends IllegalArgumentException {

        private final FailureStep failureStep;
        private final long inputRecordCount;
        private final long outputRecordCount;
        private final long abnormalHandledCount;

        public GovernanceExecutionException(
            FailureStep failureStep,
            long inputRecordCount,
            long outputRecordCount,
            long abnormalHandledCount,
            Throwable cause
        ) {
            super(formatMessage(failureStep), cause);
            this.failureStep = failureStep;
            this.inputRecordCount = inputRecordCount;
            this.outputRecordCount = outputRecordCount;
            this.abnormalHandledCount = abnormalHandledCount;
        }

        public FailureStep failureStep() {
            return failureStep;
        }

        public long inputRecordCount() {
            return inputRecordCount;
        }

        public long outputRecordCount() {
            return outputRecordCount;
        }

        public long abnormalHandledCount() {
            return abnormalHandledCount;
        }

        private static String formatMessage(FailureStep step) {
            if (step == null) {
                return "治理流程执行失败";
            }
            return "治理流程第 " + step.stepIndex() + " 步(" + step.operatorKey() + ")执行失败: " + step.reason();
        }
    }
}
