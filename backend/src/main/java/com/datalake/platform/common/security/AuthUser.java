package com.datalake.platform.common.security;

import java.util.List;

public record AuthUser(
    Long userId,
    String username,
    String role,
    String displayName,
    List<String> menus,
    List<String> actions,
    List<String> authorities
) {
}
