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
        String normalizedActions = RoleMenuCatalog.serializeConfiguredActions(roleName, RoleMenuCatalog.resolveStoredActions(roleName, role.actionPermissions()));
        if (!normalized.equals(role.menuPermissions()) || !normalizedActions.equals(role.actionPermissions())) {
            jdbcTemplate.update("update sys_role set menu_permissions = ?, action_permissions = ? where role_name = ?", normalized, normalizedActions, roleName);
        }
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
        Integer count = jdbcTemplate.queryForObject("select count(*) from operator_def", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        insertOperator("空值填充", "NULL_FILL", "CLEAN", "{\"field\":\"string\",\"fillValue\":\"string\"}", "将空值替换为指定内容");
        insertOperator("重复数据清理", "DEDUPLICATE", "DEDUP", "{\"fields\":[\"string\"]}", "按指定字段去重");
        insertOperator("字段转换", "FIELD_CONVERT", "TRANSFORM", "{\"field\":\"string\",\"transform\":\"TRIM|UPPER|LOWER|NUMBER\"}", "执行字段清洗与格式转换");
        insertOperator("条件过滤", "FILTER_KEEP", "FILTER", "{\"field\":\"string\",\"operator\":\"LIKE|EQ|GT|LT\",\"value\":\"string\"}", "仅保留符合条件的记录");
    }

    private void insertOperator(String operatorName, String operatorKey, String operatorType, String configSchema, String description) {
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
