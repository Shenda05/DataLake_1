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
            "failedTasks", 3,
            "recentFailedTasks", 2
        );
    }

    public static List<Map<String, Object>> taskTrend() {
        return List.of(
            ordered("day", "03-21", "total", 3),
            ordered("day", "03-22", "total", 4),
            ordered("day", "03-23", "total", 5),
            ordered("day", "03-24", "total", 4),
            ordered("day", "03-25", "total", 6),
            ordered("day", "03-26", "total", 5),
            ordered("day", "03-27", "total", 7)
        );
    }

    public static List<Map<String, Object>> recentTasks() {
        return List.of(
            ordered("taskId", 3001L, "taskName", "每日订单导入任务", "taskType", "IMPORT", "status", "SUCCESS", "nextRunTime", "2026-03-28 02:00"),
            ordered("taskId", 3002L, "taskName", "订单自动治理任务", "taskType", "GOVERNANCE", "status", "RUNNING", "nextRunTime", "2026-03-28 02:30")
        );
    }

    public static List<Map<String, Object>> dataSources() {
        return List.of(
            ordered("sourceId", 1001L, "sourceName", "电商文件上传目录", "sourceType", "FILE", "status", "ENABLED", "description", "订单/商品/库存演示文件源"),
            ordered("sourceId", 1002L, "sourceName", "电商业务 MySQL", "sourceType", "MYSQL", "status", "ENABLED", "description", "订单库表导入来源")
        );
    }

    public static List<Map<String, Object>> importHistory() {
        return List.of(
            ordered("importId", 5001L, "datasetName", "订单明细数据", "businessDomain", "TRADE", "formatType", "CSV", "status", "SUCCESS", "recordCount", 1280, "operator", "admin"),
            ordered("importId", 5002L, "datasetName", "库存快照数据", "businessDomain", "INVENTORY", "formatType", "JSON", "status", "FAILED", "recordCount", 0, "operator", "operator")
        );
    }

    public static List<Map<String, Object>> datasets() {
        return List.of(
            ordered("datasetId", 2001L, "datasetName", "订单明细数据", "businessDomain", "TRADE", "sourceId", 1001L, "formatType", "CSV", "recordCount", 1280, "fieldCount", 12, "status", "READY", "creator", 1L, "physicalTableName", "dl_dataset_2001"),
            ordered("datasetId", 2002L, "datasetName", "库存快照数据", "businessDomain", "INVENTORY", "sourceId", 1001L, "formatType", "JSON", "recordCount", 640, "fieldCount", 8, "status", "READY", "creator", 2L, "physicalTableName", "dl_dataset_2002")
        );
    }

    public static Map<String, Object> datasetDetail(Long datasetId) {
        return ordered(
            "datasetId", datasetId,
            "datasetName", datasetId == 2001L ? "订单明细数据" : "库存快照数据",
            "businessDomain", datasetId == 2001L ? "TRADE" : "INVENTORY",
            "sourceName", "电商文件上传目录",
            "formatType", datasetId == 2001L ? "CSV" : "JSON",
            "recordCount", datasetId == 2001L ? 1280 : 640,
            "fieldCount", datasetId == 2001L ? 12 : 8,
            "storagePath", "/storage/demo/" + datasetId,
            "status", "READY",
            "createTime", LocalDateTime.now().minusDays(2).toString()
        );
    }

    public static List<Map<String, Object>> metadata(Long datasetId) {
        if (datasetId == 2002L) {
            return List.of(
                ordered("fieldId", 1L, "datasetId", datasetId, "fieldName", "product_id", "physicalColumnName", "product_id", "fieldType", "STRING", "nullable", false, "sampleValue", "SKU-1001", "fieldOrder", 1),
                ordered("fieldId", 2L, "datasetId", datasetId, "fieldName", "stock", "physicalColumnName", "stock", "fieldType", "BIGINT", "nullable", false, "sampleValue", "12", "fieldOrder", 2),
                ordered("fieldId", 3L, "datasetId", datasetId, "fieldName", "warehouse", "physicalColumnName", "warehouse", "fieldType", "STRING", "nullable", true, "sampleValue", "华东仓", "fieldOrder", 3)
            );
        }
        return List.of(
            ordered("fieldId", 1L, "datasetId", datasetId, "fieldName", "order_id", "physicalColumnName", "order_id", "fieldType", "STRING", "nullable", false, "sampleValue", "ORD-20260327001", "fieldOrder", 1),
            ordered("fieldId", 2L, "datasetId", datasetId, "fieldName", "product_id", "physicalColumnName", "product_id", "fieldType", "STRING", "nullable", false, "sampleValue", "SKU-1001", "fieldOrder", 2),
            ordered("fieldId", 3L, "datasetId", datasetId, "fieldName", "amount", "physicalColumnName", "amount", "fieldType", "DOUBLE", "nullable", true, "sampleValue", "129.9", "fieldOrder", 3)
        );
    }

    public static PageResponse<Map<String, Object>> preview(Long datasetId) {
        List<Map<String, Object>> rows;
        if (datasetId == 2002L) {
            rows = List.of(
                ordered("rowNo", 1, "datasetId", datasetId, "product_id", "SKU-1001", "stock", 12, "warehouse", "华东仓"),
                ordered("rowNo", 2, "datasetId", datasetId, "product_id", "SKU-2001", "stock", 7, "warehouse", "华南仓")
            );
        } else {
            rows = List.of(
                ordered("rowNo", 1, "datasetId", datasetId, "order_id", "ORD-20260327001", "product_id", "SKU-1001", "amount", 129.9),
                ordered("rowNo", 2, "datasetId", datasetId, "order_id", "ORD-20260327002", "product_id", "SKU-2001", "amount", 88.0)
            );
        }
        return new PageResponse<>(1, 10, rows.size(), rows);
    }

    public static List<Map<String, Object>> queryResult() {
        return List.of(
            ordered("product_id", "SKU-1001", "order_count", 22, "sales_amount", 3820.6),
            ordered("product_id", "SKU-2001", "order_count", 14, "sales_amount", 2168.0),
            ordered("product_id", "SKU-3001", "order_count", 18, "sales_amount", 2901.5)
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
            ordered("name", "数码", "value", 52),
            ordered("name", "家居", "value", 34),
            ordered("name", "食品", "value", 26)
        );
    }

    public static List<Map<String, Object>> operators() {
        return List.of(
            ordered("operatorId", 1L, "operatorKey", "NULL_FILL", "operatorType", "CLEAN", "operatorName", "空值填充"),
            ordered("operatorId", 2L, "operatorKey", "DEDUPLICATE", "operatorType", "DEDUP", "operatorName", "重复数据清理"),
            ordered("operatorId", 3L, "operatorKey", "FIELD_CONVERT", "operatorType", "TRANSFORM", "operatorName", "字段转换"),
            ordered("operatorId", 4L, "operatorKey", "FILTER_KEEP", "operatorType", "FILTER", "operatorName", "条件过滤"),
            ordered("operatorId", 5L, "operatorKey", "ORDER_DEDUP", "operatorType", "DEDUP", "operatorName", "订单去重"),
            ordered("operatorId", 6L, "operatorKey", "AMOUNT_NORMALIZE", "operatorType", "TRANSFORM", "operatorName", "金额标准化"),
            ordered("operatorId", 7L, "operatorKey", "TIME_NORMALIZE", "operatorType", "TRANSFORM", "operatorName", "时间标准化"),
            ordered("operatorId", 8L, "operatorKey", "CATEGORY_NORMALIZE", "operatorType", "TRANSFORM", "operatorName", "商品分类标准化"),
            ordered("operatorId", 9L, "operatorKey", "STATUS_NORMALIZE", "operatorType", "TRANSFORM", "operatorName", "状态标准化")
        );
    }

    public static List<Map<String, Object>> governanceFlows() {
        return List.of(
            ordered("flowId", 7001L, "flowName", "订单自动治理流程", "inputDatasetId", 2001L, "outputDatasetId", 2101L),
            ordered("flowId", 7002L, "flowName", "商品分类标准化流程", "inputDatasetId", 2002L, "outputDatasetId", 2102L)
        );
    }

    public static List<Map<String, Object>> tasks() {
        return new ArrayList<>(recentTasks());
    }

    public static List<Map<String, Object>> taskLogs() {
        return List.of(
            ordered(
                "logId",
                4001L,
                "taskId",
                3001L,
                "taskName",
                "每日订单导入任务",
                "taskType",
                "IMPORT",
                "targetId",
                5001L,
                "status",
                "SUCCESS",
                "startTime",
                "2026-03-27 02:00",
                "endTime",
                "2026-03-27 02:03",
                "duration",
                180,
                "executionSummary",
                "导入 1280 条，0 条失败",
                "errorMessage",
                ""
            ),
            ordered(
                "logId",
                4002L,
                "taskId",
                3002L,
                "taskName",
                "订单自动治理任务",
                "taskType",
                "GOVERNANCE",
                "targetId",
                7001L,
                "status",
                "FAILED",
                "startTime",
                "2026-03-27 02:30",
                "endTime",
                "2026-03-27 02:31",
                "duration",
                60,
                "executionSummary",
                "治理执行失败",
                "errorMessage",
                "字段 order_time 时间格式不符合要求"
            )
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
