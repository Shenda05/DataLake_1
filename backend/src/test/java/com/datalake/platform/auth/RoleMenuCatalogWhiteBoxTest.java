package com.datalake.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleMenuCatalogWhiteBoxTest {

    @Test
    @DisplayName("白盒测试：管理员默认拥有用户管理菜单，普通角色没有")
    void defaultMenusDependOnRoleName() {
        assertTrue(RoleMenuCatalog.defaultMenusForRole("ADMIN").contains("users"));
        assertFalse(RoleMenuCatalog.defaultMenusForRole("ANALYST").contains("users"));
    }

    @Test
    @DisplayName("白盒测试：菜单权限会去重并按系统菜单顺序输出")
    void validateMenusDeduplicatesAndOrdersByCatalog() {
        List<String> menus = RoleMenuCatalog.validate("ANALYST", List.of("tasks", "dashboard", "tasks"));

        assertEquals(List.of("dashboard", "tasks"), menus);
    }

    @Test
    @DisplayName("白盒测试：普通角色不能分配管理员专属菜单")
    void validateMenusRejectsAdminOnlyMenuForNormalRole() {
        assertThrows(
            IllegalArgumentException.class,
            () -> RoleMenuCatalog.validate("ANALYST", List.of("dashboard", "users"))
        );
    }
}
