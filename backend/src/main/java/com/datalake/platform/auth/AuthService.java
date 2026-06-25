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
        SessionAccount user = jdbcTemplate.query(
            """
                select u.user_id,u.username,u.password,u.status,r.role_name,r.role_desc,r.menu_permissions,r.action_permissions
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
        return toLoginResponse(user, token);
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
            "select role_id, role_name, role_desc, menu_permissions, action_permissions from sys_role order by role_id",
            (rs, rowNum) -> mapRoleSummary(rs)
        );
    }

    public RoleSummary updateRole(Long roleId, SaveRoleCommand command) {
        RoleSummary existing = getRole(roleId);
        String serializedMenus = RoleMenuCatalog.serializeConfiguredMenus(existing.roleName(), command.menuPermissions());
        String serializedActions = RoleMenuCatalog.serializeConfiguredActions(existing.roleName(), command.actionPermissions());
        jdbcTemplate.update(
            """
                update sys_role
                set role_desc = ?, menu_permissions = ?, action_permissions = ?
                where role_id = ?
            """,
            normalizeRoleDesc(command.roleDesc()),
            serializedMenus,
            serializedActions,
            roleId
        );
        return getRole(roleId);
    }

    public LoginResponse currentProfile(AuthUser currentUser) {
        return toLoginResponse(loadSessionAccount(currentUser.userId(), currentUser.username()), null);
    }

    public AuthUser authenticateSession(Long userId, String username) {
        SessionAccount account = loadSessionAccount(userId, username);
        if (!"ENABLED".equalsIgnoreCase(account.status())) {
            throw new BadCredentialsException("当前账号已被停用");
        }
        List<String> actions = RoleMenuCatalog.resolveStoredActions(account.roleName(), account.actionPermissions());
        List<String> authorities = buildAuthorities(account.roleName(), actions);
        return new AuthUser(
            account.userId(),
            account.username(),
            account.roleName(),
            resolveDisplayName(account),
            RoleMenuCatalog.resolveStoredMenus(account.roleName(), account.menuPermissions()),
            actions,
            authorities
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

    private RoleSummary getRole(Long roleId) {
        RoleSummary role = jdbcTemplate.query(
            "select role_id, role_name, role_desc, menu_permissions, action_permissions from sys_role where role_id = ?",
            rs -> rs.next() ? mapRoleSummary(rs) : null,
            roleId
        );
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }
        return role;
    }

    private Timestamp now() {
        return Timestamp.from(java.time.Instant.now());
    }

    private SessionAccount mapAccount(ResultSet rs) throws SQLException {
        return new SessionAccount(
            rs.getLong("user_id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("status"),
            rs.getString("role_name"),
            rs.getString("role_desc"),
            rs.getString("menu_permissions"),
            rs.getString("action_permissions")
        );
    }

    private RoleSummary mapRoleSummary(ResultSet rs) throws SQLException {
        return new RoleSummary(
            rs.getLong("role_id"),
            rs.getString("role_name"),
            rs.getString("role_desc"),
            RoleMenuCatalog.resolveStoredMenus(rs.getString("role_name"), rs.getString("menu_permissions")),
            RoleMenuCatalog.resolveStoredActions(rs.getString("role_name"), rs.getString("action_permissions"))
        );
    }

    private String normalizeRoleDesc(String roleDesc) {
        return roleDesc == null || roleDesc.isBlank() ? null : roleDesc.trim();
    }

    private SessionAccount loadSessionAccount(Long userId, String username) {
        SessionAccount account = jdbcTemplate.query(
            """
                select u.user_id,u.username,u.password,u.status,r.role_name,r.role_desc,r.menu_permissions,r.action_permissions
                from sys_user u
                join sys_role r on r.role_id = u.role_id
                where u.user_id = ? and u.username = ?
            """,
            rs -> rs.next() ? mapAccount(rs) : null,
            userId,
            username
        );
        if (account == null) {
            throw new BadCredentialsException("当前登录用户不存在或已失效");
        }
        return account;
    }

    private LoginResponse toLoginResponse(SessionAccount user, String token) {
        return new LoginResponse(
            token,
            user.username(),
            user.roleName(),
            resolveDisplayName(user),
            RoleMenuCatalog.resolveStoredMenus(user.roleName(), user.menuPermissions()),
            RoleMenuCatalog.resolveStoredActions(user.roleName(), user.actionPermissions())
        );
    }

    private String resolveDisplayName(SessionAccount account) {
        return account.roleDesc() == null || account.roleDesc().isBlank() ? account.roleName() : account.roleDesc();
    }

    private List<String> buildAuthorities(String roleName, List<String> actions) {
        List<String> actionAuthorities = actions.stream().map(item -> "ACTION_" + item).toList();
        return java.util.stream.Stream.concat(java.util.stream.Stream.of("ROLE_" + roleName), actionAuthorities.stream()).toList();
    }

    private record SessionAccount(
        Long userId,
        String username,
        String password,
        String status,
        String roleName,
        String roleDesc,
        String menuPermissions,
        String actionPermissions
    ) {
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

    public record RoleSummary(Long roleId, String roleName, String roleDesc, List<String> menuPermissions, List<String> actionPermissions) {
    }

    public record SaveUserCommand(String username, String password, Long roleId, String status) {
    }

    public record SaveRoleCommand(String roleDesc, List<String> menuPermissions, List<String> actionPermissions) {
    }
}
