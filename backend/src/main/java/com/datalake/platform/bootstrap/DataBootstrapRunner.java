package com.datalake.platform.bootstrap;

import java.sql.Timestamp;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
        seedRoles();
        seedUsers();
        seedOperators();
    }

    private void seedRoles() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_role", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("insert into sys_role(role_name, role_desc, create_time) values(?,?,?)", "ADMIN", "平台管理员", now());
        jdbcTemplate.update("insert into sys_role(role_name, role_desc, create_time) values(?,?,?)", "OPERATOR", "数据操作员", now());
    }

    private void seedUsers() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_user", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        Long adminRoleId = jdbcTemplate.queryForObject("select role_id from sys_role where role_name='ADMIN'", Long.class);
        Long operatorRoleId = jdbcTemplate.queryForObject("select role_id from sys_role where role_name='OPERATOR'", Long.class);
        jdbcTemplate.update(
            "insert into sys_user(username,password,role_id,status,create_time,update_time) values(?,?,?,?,?,?)",
            "admin", passwordEncoder.encode("admin123"), adminRoleId, "ENABLED", now(), now()
        );
        jdbcTemplate.update(
            "insert into sys_user(username,password,role_id,status,create_time,update_time) values(?,?,?,?,?,?)",
            "operator", passwordEncoder.encode("operator123"), operatorRoleId, "ENABLED", now(), now()
        );
    }

    private void seedOperators() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from operator_def", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        insertOperator("空值填充", "NULL_FILL", "CLEAN", "{\"field\":\"\",\"value\":\"\"}", "将空值替换为指定值");
        insertOperator("重复数据清理", "DEDUPLICATE", "DEDUP", "{\"fields\":[\"field1\"]}", "按字段组合去重");
        insertOperator("字段重命名", "FIELD_CONVERT", "TRANSFORM", "{\"sourceField\":\"\",\"targetField\":\"\"}", "重命名字段");
        insertOperator("条件过滤", "FILTER_KEEP", "FILTER", "{\"field\":\"\",\"operator\":\"EQ\",\"value\":\"\"}", "按条件保留数据");
    }

    private void insertOperator(String name, String key, String type, String schema, String description) {
        jdbcTemplate.update(
            "insert into operator_def(operator_name, operator_key, operator_type, config_schema, description, status, create_time) values(?,?,?,?,?,?,?)",
            name, key, type, schema, description, "ENABLED", now()
        );
    }

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
    }
}
