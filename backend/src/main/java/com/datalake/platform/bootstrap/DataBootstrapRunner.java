package com.datalake.platform.bootstrap;

import com.datalake.platform.auth.RoleMenuCatalog;
import java.sql.Timestamp;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataBootstrapRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DataBootstrapRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
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

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
    }

    private record RolePermissionState(boolean exists, String menuPermissions, String actionPermissions) {
    }
}
