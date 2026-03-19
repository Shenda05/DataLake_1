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
        bootstrapRoles();
        bootstrapUsers();
    }

    private void bootstrapRoles() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_role", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("insert into sys_role(role_name, role_desc, create_time) values(?,?,?)", "ADMIN", "平台管理员", now());
        jdbcTemplate.update("insert into sys_role(role_name, role_desc, create_time) values(?,?,?)", "OPERATOR", "数据操作员", now());
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

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
    }
}
