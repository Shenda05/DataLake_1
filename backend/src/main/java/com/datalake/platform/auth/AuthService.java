package com.datalake.platform.auth;

import com.datalake.platform.common.security.AuthUser;
import com.datalake.platform.common.security.JwtService;
import com.datalake.platform.common.util.GeneratedKeyUtils;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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
                select u.user_id,u.username,u.status,u.role_id,r.role_name,r.role_desc,u.create_time,u.update_time
                from sys_user u
                join sys_role r on r.role_id = u.role_id
                order by u.user_id
            """,
            (rs, rowNum) -> new UserSummary(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getLong("role_id"),
                rs.getString("role_name"),
                rs.getString("role_desc"),
                rs.getString("status"),
                rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime().toString(),
                rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime().toString()
            )
        );
    }

    public List<RoleSummary> listRoles() {
        return jdbcTemplate.query(
            "select role_id, role_name, role_desc from sys_role order by role_id",
            (rs, rowNum) -> new RoleSummary(rs.getLong("role_id"), rs.getString("role_name"), rs.getString("role_desc"))
        );
    }

    public UserSummary createUser(SaveUserCommand command, Long operatorUserId) {
        validateUserCommand(command, true, null);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                """
                    insert into sys_user(username,password,role_id,status,create_time,update_time)
                    values(?,?,?,?,?,?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, command.username().trim());
            statement.setString(2, passwordEncoder.encode(command.password()));
            statement.setLong(3, command.roleId());
            statement.setString(4, normalizeStatus(command.status()));
            statement.setTimestamp(5, now());
            statement.setTimestamp(6, now());
            return statement;
        }, keyHolder);
        return getUser(GeneratedKeyUtils.getLongId(keyHolder, "user_id"));
    }

    public UserSummary updateUser(Long userId, SaveUserCommand command, Long operatorUserId) {
        UserSummary existing = getUser(userId);
        validateUserCommand(command, false, userId);
        String password = command.password() == null || command.password().isBlank()
            ? jdbcTemplate.queryForObject("select password from sys_user where user_id = ?", String.class, userId)
            : passwordEncoder.encode(command.password());
        if (operatorUserId != null && operatorUserId.equals(userId) && !"ENABLED".equalsIgnoreCase(normalizeStatus(command.status()))) {
            throw new IllegalArgumentException("不能停用当前登录用户");
        }
        jdbcTemplate.update(
            """
                update sys_user
                set username = ?, password = ?, role_id = ?, status = ?, update_time = ?
                where user_id = ?
            """,
            command.username().trim(),
            password,
            command.roleId(),
            normalizeStatus(command.status()),
            now(),
            userId
        );
        ensureAtLeastOneAdmin(existing.roleId(), command.roleId(), existing.status(), normalizeStatus(command.status()), userId, false);
        return getUser(userId);
    }

    public void deleteUser(Long userId, Long operatorUserId) {
        UserSummary existing = getUser(userId);
        if (operatorUserId != null && operatorUserId.equals(userId)) {
            throw new IllegalArgumentException("不能删除当前登录用户");
        }
        ensureAtLeastOneAdmin(existing.roleId(), null, existing.status(), null, userId, true);
        jdbcTemplate.update("delete from sys_user where user_id = ?", userId);
    }

    private void validateUserCommand(SaveUserCommand command, boolean create, Long currentUserId) {
        if (command.username() == null || command.username().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (create && (command.password() == null || command.password().isBlank())) {
            throw new IllegalArgumentException("创建用户时密码不能为空");
        }
        if (command.roleId() == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        Integer roleCount = jdbcTemplate.queryForObject("select count(*) from sys_role where role_id = ?", Integer.class, command.roleId());
        if (roleCount == null || roleCount == 0) {
            throw new IllegalArgumentException("角色不存在: " + command.roleId());
        }
        Integer duplicateCount = create
            ? jdbcTemplate.queryForObject("select count(*) from sys_user where username = ?", Integer.class, command.username().trim())
            : jdbcTemplate.queryForObject("select count(*) from sys_user where username = ? and user_id <> ?", Integer.class, command.username().trim(), currentUserId);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalArgumentException("用户名已存在: " + command.username());
        }
        normalizeStatus(command.status());
    }

    private void ensureAtLeastOneAdmin(Long originalRoleId, Long newRoleId, String originalStatus, String newStatus, Long userId, boolean deleting) {
        Long adminRoleId = jdbcTemplate.queryForObject("select role_id from sys_role where role_name = 'ADMIN'", Long.class);
        if (adminRoleId == null || !adminRoleId.equals(originalRoleId)) {
            return;
        }
        boolean removingAdmin = deleting || (newRoleId != null && !adminRoleId.equals(newRoleId)) || (newStatus != null && !"ENABLED".equalsIgnoreCase(newStatus));
        if (!removingAdmin || !"ENABLED".equalsIgnoreCase(originalStatus)) {
            return;
        }
        Integer enabledAdmins = jdbcTemplate.queryForObject(
            "select count(*) from sys_user where role_id = ? and status = 'ENABLED' and user_id <> ?",
            Integer.class,
            adminRoleId,
            userId
        );
        if (enabledAdmins == null || enabledAdmins == 0) {
            throw new IllegalArgumentException("系统至少需要保留一个启用中的管理员");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ENABLED";
        }
        String normalized = status.toUpperCase();
        if (!List.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new IllegalArgumentException("用户状态仅支持 ENABLED 或 DISABLED");
        }
        return normalized;
    }

    private UserSummary getUser(Long userId) {
        UserSummary user = jdbcTemplate.query(
            """
                select u.user_id,u.username,u.status,u.role_id,r.role_name,r.role_desc,u.create_time,u.update_time
                from sys_user u
                join sys_role r on r.role_id = u.role_id
                where u.user_id = ?
            """,
            rs -> rs.next()
                ? new UserSummary(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    rs.getLong("role_id"),
                    rs.getString("role_name"),
                    rs.getString("role_desc"),
                    rs.getString("status"),
                    rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime().toString(),
                    rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime().toString()
                )
                : null,
            userId
        );
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        return user;
    }

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
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

    public record UserSummary(
        Long userId,
        String username,
        Long roleId,
        String role,
        String roleDesc,
        String status,
        String createTime,
        String updateTime
    ) {
    }

    public record RoleSummary(Long roleId, String roleName, String roleDesc) {
    }

    public record SaveUserCommand(String username, String password, Long roleId, String status) {
    }
}
