package com.datalake.platform.common.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DemoDataFactory {

    private DemoDataFactory() {
    }

    public static List<Map<String, Object>> roles() {
        return List.of(
            ordered("roleId", 1L, "roleName", "ADMIN", "roleDesc", "平台管理员"),
            ordered("roleId", 2L, "roleName", "OPERATOR", "roleDesc", "数据操作员")
        );
    }

    public static List<Map<String, Object>> users() {
        return List.of(
            ordered("userId", 1L, "username", "admin", "role", "ADMIN", "status", "ENABLED"),
            ordered("userId", 2L, "username", "operator", "role", "OPERATOR", "status", "ENABLED")
        );
    }

    public static Map<String, Object> overview() {
        return ordered(
            "dataSources", 4,
            "datasets", 18,
            "newDatasetsToday", 3,
            "totalTasks", 26,
            "runningTasks", 2,
            "successTasks", 21,
            "failedTasks", 3
        );
    }

    public static List<Map<String, Object>> taskTrend() {
        return List.of(
            ordered("day", "03-12", "total", 3),
            ordered("day", "03-13", "total", 4),
            ordered("day", "03-14", "total", 2),
            ordered("day", "03-15", "total", 6),
            ordered("day", "03-16", "total", 5),
            ordered("day", "03-17", "total", 4),
            ordered("day", "03-18", "total", 7)
        );
    }

    public static List<Map<String, Object>> recentTasks() {
        return List.of(
            ordered("taskId", 3001L, "taskName", "每日专利数据导入", "taskType", "IMPORT", "status", "SUCCESS", "nextRunTime", "2026-03-19 09:00"),
            ordered("taskId", 3002L, "taskName", "企业画像治理流程", "taskType", "GOVERNANCE", "status", "RUNNING", "nextRunTime", "2026-03-19 10:00")
        );
    }

    public static List<Map<String, Object>> dataSources() {
        return List.of(
            ordered("sourceId", 1001L, "sourceName", "本地上传目录", "sourceType", "FILE", "status", "ENABLED", "description", "课程演示文件源"),
            ordered("sourceId", 1002L, "sourceName", "业务 MySQL", "sourceType", "MYSQL", "status", "ENABLED", "description", "P1 数据库导入来源")
        );
    }

    public static List<Map<String, Object>> importHistory() {
        return List.of(
            ordered("importId", 5001L, "datasetName", "专利基础数据", "formatType", "CSV", "status", "SUCCESS", "recordCount", 1280, "operator", "admin"),
            ordered("importId", 5002L, "datasetName", "企业画像数据", "formatType", "JSON", "status", "FAILED", "recordCount", 0, "operator", "operator")
        );
    }

    public static List<Map<String, Object>> datasets() {
        return List.of(
            ordered("datasetId", 2001L, "datasetName", "专利基础数据", "sourceId", 1001L, "formatType", "CSV", "recordCount", 1280, "fieldCount", 12, "status", "READY", "creator", "admin"),
            ordered("datasetId", 2002L, "datasetName", "企业画像数据", "sourceId", 1001L, "formatType", "JSON", "recordCount", 640, "fieldCount", 8, "status", "READY", "creator", "operator")
        );
    }

    public static Map<String, Object> datasetDetail(Long datasetId) {
        return ordered(
            "datasetId", datasetId,
            "datasetName", datasetId == 2001L ? "专利基础数据" : "企业画像数据",
            "sourceName", "本地上传目录",
            "formatType", datasetId == 2001L ? "CSV" : "JSON",
            "recordCount", datasetId == 2001L ? 1280 : 640,
            "fieldCount", datasetId == 2001L ? 12 : 8,
            "storagePath", "/storage/demo/" + datasetId,
            "status", "READY",
            "createTime", LocalDateTime.now().minusDays(2).toString()
        );
    }

    public static List<Map<String, Object>> metadata(Long datasetId) {
        return List.of(
            ordered("fieldId", 1L, "datasetId", datasetId, "fieldName", "patent_code", "fieldType", "STRING", "nullable", false, "sampleValue", "CN20250001"),
            ordered("fieldId", 2L, "datasetId", datasetId, "fieldName", "company_name", "fieldType", "STRING", "nullable", false, "sampleValue", "示例科技"),
            ordered("fieldId", 3L, "datasetId", datasetId, "fieldName", "industry", "fieldType", "STRING", "nullable", true, "sampleValue", "智能制造")
        );
    }

    public static PageResponse<Map<String, Object>> preview(Long datasetId) {
        List<Map<String, Object>> rows = List.of(
            ordered("rowNo", 1, "datasetId", datasetId, "patent_code", "CN20250001", "company_name", "示例科技", "industry", "智能制造"),
            ordered("rowNo", 2, "datasetId", datasetId, "patent_code", "CN20250002", "company_name", "未来工业", "industry", "新能源")
        );
        return new PageResponse<>(1, 10, rows.size(), rows);
    }

    public static List<Map<String, Object>> queryResult() {
        return List.of(
            ordered("company_name", "示例科技", "industry", "智能制造", "patent_count", 22),
            ordered("company_name", "未来工业", "industry", "新能源", "patent_count", 14),
            ordered("company_name", "启明数据", "industry", "人工智能", "patent_count", 18)
        );
    }

    public static Map<String, Object> analysisSummary(Long datasetId) {
        return ordered(
            "datasetId", datasetId,
            "recordCount", 1280,
            "nullCount", 32,
            "duplicateCount", 18,
            "maxValue", 97,
            "minValue", 1
        );
    }

    public static List<Map<String, Object>> chartSeries(Long datasetId) {
        return List.of(
            ordered("name", "智能制造", "value", 52),
            ordered("name", "新能源", "value", 34),
            ordered("name", "人工智能", "value", 26)
        );
    }

    public static List<Map<String, Object>> operators() {
        return List.of(
            ordered("operatorId", 1L, "operatorKey", "NULL_FILL", "operatorType", "CLEAN", "operatorName", "空值填充"),
            ordered("operatorId", 2L, "operatorKey", "DEDUPLICATE", "operatorType", "DEDUP", "operatorName", "重复数据清理"),
            ordered("operatorId", 3L, "operatorKey", "FIELD_CONVERT", "operatorType", "TRANSFORM", "operatorName", "字段转换"),
            ordered("operatorId", 4L, "operatorKey", "FILTER_KEEP", "operatorType", "FILTER", "operatorName", "条件过滤")
        );
    }

    public static List<Map<String, Object>> governanceFlows() {
        return List.of(
            ordered("flowId", 7001L, "flowName", "企业画像清洗", "inputDatasetId", 2002L, "outputDatasetId", 2102L),
            ordered("flowId", 7002L, "flowName", "专利数据标准化", "inputDatasetId", 2001L, "outputDatasetId", 2101L)
        );
    }

    public static List<Map<String, Object>> tasks() {
        return new ArrayList<>(recentTasks());
    }

    public static List<Map<String, Object>> taskLogs() {
        return List.of(
            ordered("logId", 4001L, "taskId", 3001L, "taskName", "每日专利数据导入", "status", "SUCCESS", "startTime", "2026-03-18 09:00", "endTime", "2026-03-18 09:03", "duration", 180, "message", "导入 1280 条，0 条失败"),
            ordered("logId", 4002L, "taskId", 3002L, "taskName", "企业画像治理流程", "status", "FAILED", "startTime", "2026-03-18 14:00", "endTime", "2026-03-18 14:01", "duration", 60, "message", "字段 industry 为空值比例过高")
        );
    }

    public static Map<String, Object> ordered(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i].toString(), values[i + 1]);
        }
        return map;
    }
}

