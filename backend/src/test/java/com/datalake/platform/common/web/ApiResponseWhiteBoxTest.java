package com.datalake.platform.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseWhiteBoxTest {

    @Test
    @DisplayName("白盒测试：success(data) 生成统一成功响应")
    void successWithDataContainsOkCodeAndPayload() {
        ApiResponse<Map<String, Object>> response = ApiResponse.success(Map.of("name", "demo"), "req-1");

        assertEquals(0, response.code());
        assertEquals("OK", response.message());
        assertEquals("demo", response.data().get("name"));
        assertEquals("req-1", response.requestId());
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("白盒测试：success() 生成无数据成功响应")
    void successWithoutDataContainsNullPayload() {
        ApiResponse<Void> response = ApiResponse.success("req-2");

        assertEquals(0, response.code());
        assertEquals("OK", response.message());
        assertNull(response.data());
        assertEquals("req-2", response.requestId());
    }

    @Test
    @DisplayName("白盒测试：failure 生成统一失败响应")
    void failureContainsErrorCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.failure(400, "bad request", "req-3");

        assertEquals(400, response.code());
        assertEquals("bad request", response.message());
        assertNull(response.data());
        assertEquals("req-3", response.requestId());
    }
}
