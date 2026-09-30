package com.education.base.security;

import com.education.base.config.AuthSecurityConfig;
import com.education.base.controller.AuthController;
import com.education.base.dto.request.LoginRequest;
import com.education.base.exception.UnauthorizedException;
import com.education.base.service.AuthService;
import com.education.base.support.WebMvcSecurityTestConfig;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filter giới hạn tần suất chạy thật trong chuỗi servlet (sau Spring Security): trả 429 kèm header CORS.
 */
@WebMvcTest(AuthController.class)
@Import({WebMvcSecurityTestConfig.class, AuthSecurityConfig.class})
@TestPropertySource(properties = {
        "app.security.auth.rate-limit.login-per-username=2",
        "app.security.auth.rate-limit.login-per-ip=100"
})
class AuthRateLimitWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void login_tooManyAttemptsForSameUsername_returns429() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng."));
        String body = "{\"username\":\"ratelimited\",\"password\":\"wrong\"}";

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        verify(authService, times(2)).login(any(LoginRequest.class));
    }
}
