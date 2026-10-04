package com.education.base.security;

import com.education.base.config.AuthSecurityConfig;
import com.education.base.controller.AuthController;
import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.service.AuthService;
import com.education.base.service.AuthTokens;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import com.education.base.dto.response.AuthUserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RequestRateLimitFilter} chạy thật trong chuỗi servlet (sau Spring Security: biết người dùng, đã có header
 * CORS) với quy tắc lấy từ {@code application.yml} (ngưỡng ghi đè bằng property như biến môi trường).
 */
@WebMvcTest(AuthController.class)
@Import({WebMvcSecurityTestConfig.class, AuthSecurityConfig.class})
@TestPropertySource(properties = "app.security.rate-limit.rules.change-password.per-user=2")
class RequestRateLimitWebTest {

    private static final String BODY = "{\"oldPassword\":\"Old@1234\",\"newPassword\":\"NewPass@456\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    @WithAuthUser(id = 41L, username = "hs41", roles = "ROLE_TEACHER")
    void changePassword_overBudget_returns429WithRetryAfterAndCors() throws Exception {
        when(authService.changePassword(eq(41L), any(ChangePasswordRequest.class))).thenReturn(AuthTokens.builder()
                .accessToken("a").refreshToken("r").accessTokenTtlSeconds(60).refreshTokenTtlSeconds(600)
                .user(AuthUserResponse.builder().id(41L).username("hs41").build())
                .build());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        verify(authService, times(2)).changePassword(eq(41L), any(ChangePasswordRequest.class));
    }
}
