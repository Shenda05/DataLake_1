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
}
