package com.datalake.platform.auth;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class RoleMenuCatalog {

    public static final List<String> ALL_MENUS = List.of(
        "dashboard",
        "data-sources",
        "imports",
        "datasets",
        "queries",
        "governance",
        "tasks",
        "logs",
        "users"
    );

    private static final List<String> ADMIN_ONLY_MENUS = List.of("users");
    public static final List<String> ALL_ACTIONS = List.of(
        "source.manage",
        "import.database",
        "dataset.delete",
        "governance.manage",
        "governance.execute",
        "task.manage",
        "task.trigger",
        "dataset.export",
        "query.export",
        "log.export",
        "user.manage",
        "role.manage",
        "log.replay"
    );
    private static final List<String> ADMIN_ONLY_ACTIONS = List.of(
        "source.manage",
        "import.database",
        "dataset.delete",
        "user.manage",
        "role.manage"
    );

    private RoleMenuCatalog() {
    }

    public static List<String> defaultMenusForRole(String roleName) {
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            return ALL_MENUS;
        }
        return ALL_MENUS.stream().filter(item -> !ADMIN_ONLY_MENUS.contains(item)).toList();
    }

    public static List<String> resolveStoredMenus(String roleName, String serializedMenus) {
        if (serializedMenus == null || serializedMenus.isBlank()) {
            return defaultMenusForRole(roleName);
        }
        try {
            return validate(roleName, Arrays.stream(serializedMenus.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList());
        } catch (IllegalArgumentException ex) {
            return defaultMenusForRole(roleName);
        }
    }

    public static String serializeConfiguredMenus(String roleName, List<String> menus) {
        return String.join(",", validate(roleName, menus));
    }

    public static List<String> defaultActionsForRole(String roleName) {
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            return ALL_ACTIONS;
        }
        return List.of(
            "governance.manage",
            "governance.execute",
            "task.manage",
            "task.trigger",
            "dataset.export",
            "query.export",
            "log.export",
            "log.replay"
        );
    }

    public static List<String> legacyDefaultActionsForRole(String roleName) {
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            return List.of(
                "source.manage",
                "import.database",
                "dataset.delete",
                "governance.manage",
                "governance.execute",
                "task.manage",
                "task.trigger",
                "dataset.export",
                "query.export",
                "user.manage",
                "role.manage",
                "log.replay"
            );
        }
        return List.of(
            "governance.manage",
            "governance.execute",
            "task.manage",
            "task.trigger",
            "dataset.export",
            "query.export",
            "log.replay"
        );
    }

    public static List<String> resolveStoredActions(String roleName, String serializedActions) {
        if (serializedActions == null) {
            return defaultActionsForRole(roleName);
        }
        try {
            return validateActions(roleName, Arrays.stream(serializedActions.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList());
        } catch (IllegalArgumentException ex) {
            return defaultActionsForRole(roleName);
        }
    }

    public static String serializeConfiguredActions(String roleName, List<String> actions) {
        return String.join(",", validateActions(roleName, actions));
    }

    public static List<String> validate(String roleName, List<String> menus) {
        if (menus == null || menus.isEmpty()) {
            throw new IllegalArgumentException("请至少保留一个可访问菜单");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String menu : menus) {
            if (menu == null || menu.isBlank()) {
                continue;
            }
            if (!ALL_MENUS.contains(menu)) {
                throw new IllegalArgumentException("存在不支持的菜单权限: " + menu);
            }
            if (!"ADMIN".equalsIgnoreCase(roleName) && ADMIN_ONLY_MENUS.contains(menu)) {
                throw new IllegalArgumentException("非管理员角色不能分配“用户与权限”菜单");
            }
            normalized.add(menu);
        }
        List<String> orderedMenus = ALL_MENUS.stream().filter(normalized::contains).toList();
        if (orderedMenus.isEmpty()) {
            throw new IllegalArgumentException("请至少保留一个可访问菜单");
        }
        if ("ADMIN".equalsIgnoreCase(roleName) && !orderedMenus.contains("users")) {
            throw new IllegalArgumentException("管理员角色必须保留“用户与权限”菜单");
        }
        return orderedMenus;
    }

    public static List<String> validateActions(String roleName, List<String> actions) {
        if (actions == null) {
            return defaultActionsForRole(roleName);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String action : actions) {
            if (action == null || action.isBlank()) {
                continue;
            }
            if (!ALL_ACTIONS.contains(action)) {
                throw new IllegalArgumentException("存在不支持的操作权限: " + action);
            }
            if (!"ADMIN".equalsIgnoreCase(roleName) && ADMIN_ONLY_ACTIONS.contains(action)) {
                throw new IllegalArgumentException("当前角色不能分配管理类操作权限: " + action);
            }
            normalized.add(action);
        }
        List<String> orderedActions = ALL_ACTIONS.stream().filter(normalized::contains).toList();
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            List<String> requiredAdminActions = List.of("user.manage", "role.manage");
            if (!orderedActions.containsAll(requiredAdminActions)) {
                throw new IllegalArgumentException("管理员角色必须保留用户和角色管理权限");
            }
        }
        return orderedActions;
    }
}
