package com.datalake.platform.auth;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datalake.platform.common.web.RequestIdFilter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerBlackBoxTest {

    private static final String REQUEST_ID = "test-request-id";

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();
    }

    @Test
    @DisplayName("黑盒测试：退出登录接口返回统一成功响应")
    void logoutReturnsSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .requestAttr(RequestIdFilter.REQUEST_ID_ATTR, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.message").value("OK"))
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }

    @Test
    @DisplayName("黑盒测试：登录接口返回 token、角色和权限信息")
    void loginReturnsTokenAndPermissionPayload() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse(
            "demo-token",
            "admin",
            "ADMIN",
            "系统管理员",
            List.of("dashboard", "users"),
            List.of("user.manage")
        ));

        mockMvc.perform(post("/api/auth/login")
                .requestAttr(RequestIdFilter.REQUEST_ID_ATTR, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "password": "123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.token").value("demo-token"))
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.role").value("ADMIN"))
            .andExpect(jsonPath("$.data.menus", contains("dashboard", "users")));

        verify(authService).login(any(LoginRequest.class));
    }
}
