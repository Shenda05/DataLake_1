package com.datalake.platform.auth;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-seconds}")
    private long expireSeconds;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Map<String, String> admin = Map.of("username", "admin", "password", "admin123", "role", "ADMIN", "displayName", "平台管理员");
        Map<String, String> operator = Map.of("username", "operator", "password", "operator123", "role", "OPERATOR", "displayName", "数据操作员");
        Map<String, String> matched = request.username().equals("admin") ? admin : operator;
        if (!matched.get("username").equals(request.username()) || !matched.get("password").equals(request.password())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String role = matched.get("role");
        List<String> menus = role.equals("ADMIN")
            ? List.of("dashboard", "data-sources", "imports", "datasets", "queries", "governance", "tasks", "logs", "users")
            : List.of("dashboard", "imports", "datasets", "queries", "governance", "tasks", "logs");

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
            .subject(matched.get("username"))
            .claim("role", role)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(expireSeconds)))
            .signWith(key)
            .compact();

        return ApiResponse.success(
            new LoginResponse(token, matched.get("username"), role, matched.get("displayName"), menus),
            requestId(httpRequest)
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        return ApiResponse.success(requestId(request));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(HttpServletRequest request) {
        return ApiResponse.success(
            Map.of("username", "admin", "role", "ADMIN", "displayName", "平台管理员"),
            requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
    }
}

