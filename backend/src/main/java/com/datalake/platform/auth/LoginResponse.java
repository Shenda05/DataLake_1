package com.datalake.platform.auth;

import java.util.List;

public record LoginResponse(
    String token,
    String username,
    String role,
    String displayName,
    List<String> menus
) {
}

