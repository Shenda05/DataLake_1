package com.datalake.platform.common.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthUser authUser) {
            return authUser;
        }
        throw new IllegalStateException("当前登录状态无效");
    }
}
