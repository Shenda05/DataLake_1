package com.datalake.platform.bootstrap;

import com.datalake.platform.auth.RoleMenuCatalog;
import com.datalake.platform.dataset.DatasetService;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataBootstrapRunner implements ApplicationRunner {

    private static final String GOVERNANCE_DEMO_DATASET_NAME = "治理演示_订单脏数据";
    private static final String GOVERNANCE_DEMO_FLOW_NAME = "治理演示_订单自动治理流程";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DatasetService datasetService;

    public DataBootstrapRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, DatasetService datasetService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.datasetService = datasetService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureRoleMenuPermissionsColumn();
        ensureRoleActionPermissionsColumn();
        ensureDatasetBusinessDomainColumn();
        ensureImportBusinessDomainColumn();
        ensureTaskLogStructuredColumns();
        bootstrapRoles();
        bootstrapUsers();
        bootstrapOperators();
        bootstrapGovernanceDemoDataset();
    }

    private void ensureRoleMenuPermissionsColumn() {
        try {
            jdbcTemplate.queryForList("select menu_permissions from sys_role where 1 = 0");
        } catch (DataAccessException ex) {
            jdbcTemplate.execute("alter table sys_role add column menu_permissions varchar(2000)");
        }
    }

    private void ensureRoleActionPermissionsColumn() {
        try {
            jdbcTemplate.queryForList("select action_permissions from sys_role where 1 = 0");
        } catch (DataAccessException ex) {
            jdbcTemplate.execute("alter table sys_role add column action_permissions varchar(2000)");
        }
    }

    private void ensureDatasetBusinessDomainColumn() {
        try {
            jdbcTemplate.queryForList("select business_domain from data_set where 1 = 0");
        } catch (DataAccessException ex) {
            jdbcTemplate.execute("alter table data_set add column business_domain varchar(32) default 'TRADE'");
        }
        jdbcTemplate.update("update data_set set business_domain = 'TRADE' where business_domain is null or trim(business_domain) = ''");
    }

    private void ensureImportBusinessDomainColumn() {
        try {
            jdbcTemplate.queryForList("select business_domain from import_record where 1 = 0");
        } catch (DataAccessException ex) {
            jdbcTemplate.execute("alter table import_record add column business_domain varchar(32) default 'TRADE'");
        }
        jdbcTemplate.update("update import_record set business_domain = 'TRADE' where business_domain is null or trim(business_domain) = ''");
    }

    private void ensureTaskLogStructuredColumns() {
        ensureColumnIfMissing("task_log", "input_params", "varchar(4000)");
        ensureColumnIfMissing("task_log", "execution_steps", "varchar(4000)");
        ensureColumnIfMissing("task_log", "failure_reason", "varchar(4000)");
    }

    private void ensureColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        try {
            jdbcTemplate.queryForList("select " + columnName + " from " + tableName + " where 1 = 0");
        } catch (DataAccessException ex) {
            jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + columnDefinition);
        }
    }

    private void bootstrapRoles() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_role", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                "insert into sys_role(role_name, role_desc, menu_permissions, action_permissions, create_time) values(?,?,?,?,?)",
                "ADMIN",
                "平台管理员",
                RoleMenuCatalog.serializeConfiguredMenus("ADMIN", RoleMenuCatalog.defaultMenusForRole("ADMIN")),
                RoleMenuCatalog.serializeConfiguredActions("ADMIN", RoleMenuCatalog.defaultActionsForRole("ADMIN")),
                now()
            );
            jdbcTemplate.update(
                "insert into sys_role(role_name, role_desc, menu_permissions, action_permissions, create_time) values(?,?,?,?,?)",
                "OPERATOR",
                "数据操作员",
                RoleMenuCatalog.serializeConfiguredMenus("OPERATOR", RoleMenuCatalog.defaultMenusForRole("OPERATOR")),
                RoleMenuCatalog.serializeConfiguredActions("OPERATOR", RoleMenuCatalog.defaultActionsForRole("OPERATOR")),
                now()
            );
            return;
        }
        backfillRolePermissions("ADMIN");
        backfillRolePermissions("OPERATOR");
    }

    private void backfillRolePermissions(String roleName) {
        RolePermissionState role = jdbcTemplate.query(
            "select menu_permissions, action_permissions from sys_role where role_name = ?",
            rs -> rs.next() ? new RolePermissionState(true, rs.getString("menu_permissions"), rs.getString("action_permissions")) : new RolePermissionState(false, null, null),
            roleName
        );
        if (role == null || !role.exists()) {
            return;
        }
        String normalized = RoleMenuCatalog.serializeConfiguredMenus(roleName, RoleMenuCatalog.resolveStoredMenus(roleName, role.menuPermissions()));
        String normalizedActions = normalizeRoleActions(roleName, role.actionPermissions());
        if (!normalized.equals(role.menuPermissions()) || !normalizedActions.equals(role.actionPermissions())) {
            jdbcTemplate.update("update sys_role set menu_permissions = ?, action_permissions = ? where role_name = ?", normalized, normalizedActions, roleName);
        }
    }

    private String normalizeRoleActions(String roleName, String storedActions) {
        if (storedActions == null) {
            return RoleMenuCatalog.serializeConfiguredActions(roleName, RoleMenuCatalog.defaultActionsForRole(roleName));
        }
        String legacyDefaults = RoleMenuCatalog.serializeConfiguredActions(roleName, RoleMenuCatalog.legacyDefaultActionsForRole(roleName));
        if (legacyDefaults.equals(storedActions)) {
            return RoleMenuCatalog.serializeConfiguredActions(roleName, RoleMenuCatalog.defaultActionsForRole(roleName));
        }
        return RoleMenuCatalog.serializeConfiguredActions(roleName, RoleMenuCatalog.resolveStoredActions(roleName, storedActions));
    }

    private void bootstrapUsers() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_user", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        Long adminRoleId = jdbcTemplate.queryForObject("select role_id from sys_role where role_name = 'ADMIN'", Long.class);
        Long operatorRoleId = jdbcTemplate.queryForObject("select role_id from sys_role where role_name = 'OPERATOR'", Long.class);
        jdbcTemplate.update(
            "insert into sys_user(username,password,role_id,status,create_time,update_time) values(?,?,?,?,?,?)",
            "admin",
            passwordEncoder.encode("admin123"),
            adminRoleId,
            "ENABLED",
            now(),
            now()
        );
        jdbcTemplate.update(
            "insert into sys_user(username,password,role_id,status,create_time,update_time) values(?,?,?,?,?,?)",
            "operator",
            passwordEncoder.encode("operator123"),
            operatorRoleId,
            "ENABLED",
            now(),
            now()
        );
    }

    private void bootstrapOperators() {
        // [Ecom-MVP Completed] 保留通用算子，同时补齐电商治理算子
        ensureOperator("空值填充", "NULL_FILL", "CLEAN", "{\"field\":\"string\",\"fillValue\":\"string\"}", "将空值替换为指定内容");
        ensureOperator("重复数据清理", "DEDUPLICATE", "DEDUP", "{\"fields\":[\"string\"]}", "按指定字段去重");
        ensureOperator("字段转换", "FIELD_CONVERT", "TRANSFORM", "{\"field\":\"string\",\"transform\":\"TRIM|UPPER|LOWER|NUMBER\"}", "执行字段清洗与格式转换");
        ensureOperator("条件过滤", "FILTER_KEEP", "FILTER", "{\"field\":\"string\",\"operator\":\"LIKE|EQ|GT|LT\",\"value\":\"string\"}", "仅保留符合条件的记录");
        ensureOperator("订单去重", "ORDER_DEDUP", "DEDUP", "{\"field\":\"order_id\"}", "按订单主键去重");
        ensureOperator("金额标准化", "AMOUNT_NORMALIZE", "TRANSFORM", "{\"field\":\"amount\"}", "统一金额格式为两位小数");
        ensureOperator("时间标准化", "TIME_NORMALIZE", "TRANSFORM", "{\"field\":\"order_time\"}", "统一时间格式为 ISO 时间");
        ensureOperator("商品分类标准化", "CATEGORY_NORMALIZE", "TRANSFORM", "{\"field\":\"category\"}", "规范商品分类值");
        ensureOperator("状态标准化", "STATUS_NORMALIZE", "TRANSFORM", "{\"field\":\"status\"}", "规范订单或支付状态值");
    }

    private void ensureOperator(String operatorName, String operatorKey, String operatorType, String configSchema, String description) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from operator_def where operator_key = ?", Integer.class, operatorKey);
        if (count != null && count > 0) {
            jdbcTemplate.update(
                "update operator_def set operator_name = ?, operator_type = ?, config_schema = ?, description = ? where operator_key = ?",
                operatorName,
                operatorType,
                configSchema,
                description,
                operatorKey
            );
            return;
        }
        jdbcTemplate.update(
            "insert into operator_def(operator_name,operator_key,operator_type,config_schema,description,status,create_time) values(?,?,?,?,?,?,?)",
            operatorName,
            operatorKey,
            operatorType,
            configSchema,
            description,
            "ENABLED",
            now()
        );
    }

    private void bootstrapGovernanceDemoDataset() {
        Long datasetId = findDatasetId(GOVERNANCE_DEMO_DATASET_NAME);
        if (datasetId == null) {
            Long adminUserId = adminUserId();
            DatasetService.CreatedDataset dataset = datasetService.createDatasetFromRows(
                null,
                GOVERNANCE_DEMO_DATASET_NAME,
                "TRADE",
                "SQL",
                "bootstrap://governance-demo-orders",
                "启动时自动生成的治理演示数据：重复订单、金额格式不统一、时间格式不统一、状态值不统一",
                adminUserId,
                governanceDemoColumns(),
                governanceDemoRows()
            );
            datasetId = dataset.datasetId();
        }
        ensureGovernanceDemoFlow(datasetId);
    }

    private Long findDatasetId(String datasetName) {
        return jdbcTemplate.query(
            "select dataset_id from data_set where dataset_name = ?",
            rs -> rs.next() ? rs.getLong("dataset_id") : null,
            datasetName
        );
    }

    private Long adminUserId() {
        Long userId = jdbcTemplate.query(
            "select user_id from sys_user where username = 'admin'",
            rs -> rs.next() ? rs.getLong("user_id") : null
        );
        return userId == null ? 1L : userId;
    }

    private List<DatasetService.MetaFieldRecord> governanceDemoColumns() {
        return List.of(
            new DatasetService.MetaFieldRecord(null, null, "order_id", "order_id", "VARCHAR", false, "ORD-1001", 1),
            new DatasetService.MetaFieldRecord(null, null, "amount", "amount", "VARCHAR", true, "￥1,299.90", 2),
            new DatasetService.MetaFieldRecord(null, null, "order_time", "order_time", "VARCHAR", true, "2026/06/20 09:15:00", 3),
            new DatasetService.MetaFieldRecord(null, null, "order_status", "order_status", "VARCHAR", true, "paid", 4),
            new DatasetService.MetaFieldRecord(null, null, "product_name", "product_name", "VARCHAR", true, "无线耳机", 5),
            new DatasetService.MetaFieldRecord(null, null, "category", "category", "VARCHAR", true, "手机配件", 6),
            new DatasetService.MetaFieldRecord(null, null, "buyer_city", "buyer_city", "VARCHAR", true, "上海", 7)
        );
    }

    private List<Map<String, Object>> governanceDemoRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(order("ORD-1001", "￥1,299.90", "2026/06/20 09:15:00", "paid", "无线耳机 Pro", "手机配件", "上海"));
        rows.add(order("ORD-1001", "1299.9", "2026-06-20 09:15", "支付成功", "无线耳机 Pro", "手机配件", "上海"));
        rows.add(order("ORD-1002", " 88元 ", "2026-06-21 10:30:00", "pending", "运动T恤", "服装", "杭州"));
        rows.add(order("ORD-1003", "$45.678", "2026/06/22 11:00", "cancelled", "进口饼干", "食品", "广州"));
        rows.add(order("ORD-1004", "", "2026-06-23", "failed", "电饭煲", "家电", "深圳"));
        rows.add(order("ORD-1005", "1,999", "2026/06/23 20:18:30", "completed", "智能手机 X", "手机数码", "北京"));
        rows.add(order("ORD-1006", "￥599.5", "2026-06-24 08:05", "created", "蓝牙音箱", "3C", "成都"));
        rows.add(order("ORD-1007", "299 RMB", "2026/06/24 09:20", "已支付", "跑步鞋", "服装鞋包", "武汉"));
        rows.add(order("ORD-1007", "299.00", "2026-06-24 09:20:00", "success", "跑步鞋", "服装鞋包", "武汉"));
        rows.add(order("ORD-1008", "76.8元", "2026-06-24 12:00:00", "refunded", "坚果礼盒", "食品生鲜", "南京"));
        rows.add(order("ORD-1009", "￥3499.00", "2026/06/24 14:30:00", "PAID", "扫地机器人", "家用电器", "苏州"));
        rows.add(order("ORD-1010", "abc", "2026-06-24 16:45", "pending", "儿童玩具", "母婴玩具", "宁波"));
        rows.add(order("ORD-1011", "0", "2026/06/25 09:00:00", "closed", "售后补偿券", "虚拟商品", "上海"));
        rows.add(order("ORD-1012", "￥109.456", "2026-06-25 09:12:35", "待支付", "厨房刀具", "家电厨具", "厦门"));
        rows.add(order("ORD-1013", "$18.2", "2026/06/25 10:18", "canceled", "咖啡豆", "food", "青岛"));
        rows.add(order("ORD-1014", "666.666", "2026-06-25 11:11:11", "支付成功", "平板电脑", "phone", "重庆"));
        rows.add(order("ORD-1015", "  35  ", "2026/06/25 12:00:00", "new", "数据线", "手机配件", "天津"));
        rows.add(order("ORD-1016", "￥249.9", "2026-06-25 13:05", "失败", "行李箱", "旅行用品", "西安"));
        rows.add(order("ORD-1017", "899元", "2026/06/25 14:40", "paid", "空气炸锅", "appliance", "合肥"));
        rows.add(order("ORD-1018", "￥59.90", "2026-06-25 15:45:00", "已退款", "洗衣液", "日用百货", "长沙"));
        rows.add(order("ORD-1018", "59.9", "2026/06/25 15:45", "refunded", "洗衣液", "日用百货", "长沙"));
        rows.add(order("ORD-1019", "420.00", "2026-06-25 16:00:00", "paid", "衬衫", "clothes", "郑州"));
        rows.add(order("ORD-1020", "￥15.5", "2026/06/25 17:30:00", "pending", "矿泉水", "食品", "济南"));
        rows.add(order("ORD-1020", "15.50", "2026-06-25 17:30", "created", "矿泉水", "食品", "济南"));
        return rows;
    }

    private Map<String, Object> order(
        String orderId,
        String amount,
        String orderTime,
        String orderStatus,
        String productName,
        String category,
        String buyerCity
    ) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("order_id", orderId);
        row.put("amount", amount);
        row.put("order_time", orderTime);
        row.put("order_status", orderStatus);
        row.put("product_name", productName);
        row.put("category", category);
        row.put("buyer_city", buyerCity);
        return row;
    }

    private void ensureGovernanceDemoFlow(Long datasetId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from governance_flow where flow_name = ?",
            Integer.class,
            GOVERNANCE_DEMO_FLOW_NAME
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                "update governance_flow set input_dataset_id = ?, operator_chain = ?, update_time = ? where flow_name = ?",
                datasetId,
                governanceDemoOperatorChain(),
                now(),
                GOVERNANCE_DEMO_FLOW_NAME
            );
            return;
        }
        jdbcTemplate.update(
            "insert into governance_flow(flow_name,input_dataset_id,operator_chain,creator,create_time,update_time) values(?,?,?,?,?,?)",
            GOVERNANCE_DEMO_FLOW_NAME,
            datasetId,
            governanceDemoOperatorChain(),
            adminUserId(),
            now(),
            now()
        );
    }

    private String governanceDemoOperatorChain() {
        return """
            [
              {"operatorKey":"ORDER_DEDUP","params":{"field":"order_id"}},
              {"operatorKey":"AMOUNT_NORMALIZE","params":{"field":"amount"}},
              {"operatorKey":"TIME_NORMALIZE","params":{"field":"order_time"}},
              {"operatorKey":"STATUS_NORMALIZE","params":{"field":"order_status"}}
            ]
            """;
    }

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
    }

    private record RolePermissionState(boolean exists, String menuPermissions, String actionPermissions) {
    }
}
