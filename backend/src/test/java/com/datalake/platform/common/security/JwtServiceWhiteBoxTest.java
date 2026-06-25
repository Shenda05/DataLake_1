package com.datalake.platform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceWhiteBoxTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("白盒测试：生成的 JWT 可以解析出用户和角色信息")
    void generatedTokenCanBeParsedBackToClaims() {
        JwtService jwtService = new JwtService(SECRET, 3600);

        String token = jwtService.generateToken(7L, "admin", "ADMIN");
        Claims claims = jwtService.parseClaims(token);

        assertNotNull(token);
        assertEquals("admin", claims.getSubject());
        assertEquals(7, claims.get("userId"));
        assertEquals("ADMIN", claims.get("role"));
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("白盒测试：篡改后的 JWT 不能通过验签")
    void tamperedTokenCannotBeParsed() {
        JwtService jwtService = new JwtService(SECRET, 3600);
        String token = jwtService.generateToken(7L, "admin", "ADMIN") + "x";

        assertThrows(JwtException.class, () -> jwtService.parseClaims(token));
    }
}
