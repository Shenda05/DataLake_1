package com.datalake.platform.auth;

import com.datalake.platform.common.security.AuthUser;
import com.datalake.platform.common.security.JwtService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount user = jdbcTemplate.query(
            """
                select u.user_id,u.username,u.password,u.status,r.role_name
                from sys_user u
                join sys_role r on r.role_id = u.role_id
                where u.username = ?
            """,
            rs -> rs.next() ? mapAccount(rs) : null,
            request.username()
        );
        if (user == null || !"ENABLED".equalsIgnoreCase(user.status()) || !passwordEncoder.matches(request.password(), user.password())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        String token = jwtService.generateToken(user.userId(), user.username(), user.roleName());
        return new LoginResponse(token, user.username(), user.roleName(), user.roleName().equals("ADMIN") ? "平台管理员" : "数据操作员", menus(user.roleName()));
    }

    public List<UserSummary> listUsers() {
        return jdbcTemplate.query(
            """
                select u.user_id,u.username,u.status,r.role_name
                from sys_user u
                join sys_role r on r.role_id = u.role_id
                order by u.user_id
            """,
            (rs, rowNum) -> new UserSummary(rs.getLong("user_id"), rs.getString("username"), rs.getString("role_name"), rs.getString("status"))
        );
    }

    public List<RoleSummary> listRoles() {
        return jdbcTemplate.query(
            "select role_id, role_name, role_desc from sys_role order by role_id",
            (rs, rowNum) -> new RoleSummary(rs.getLong("role_id"), rs.getString("role_name"), rs.getString("role_desc"))
        );
    }

    private UserAccount mapAccount(ResultSet rs) throws SQLException {
        return new UserAccount(
            rs.getLong("user_id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("status"),
            rs.getString("role_name")
        );
    }

    private List<String> menus(String roleName) {
        if ("ADMIN".equals(roleName)) {
            return List.of("dashboard", "data-sources", "imports", "datasets", "queries", "governance", "tasks", "logs", "users");
        }
        return List.of("dashboard", "imports", "datasets", "queries", "governance", "tasks", "logs");
    }

    private record UserAccount(Long userId, String username, String password, String status, String roleName) {
    }

    public record UserSummary(Long userId, String username, String role, String status) {
    }

    public record RoleSummary(Long roleId, String roleName, String roleDesc) {
    }
}
