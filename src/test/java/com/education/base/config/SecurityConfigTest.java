package com.education.base.config;

import com.education.base.controller.HealthController;
import com.education.base.controller.HomeController;
import com.education.base.security.JwtTokenService;
import com.education.base.service.AccessControlService;
import com.education.base.support.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quy tắc truy cập ngoài {@code /api/**} (deny mặc định) và header bảo mật (CSP, HSTS).
 */
@WebMvcTest({HealthController.class, HomeController.class})
@Import(WebMvcSecurityTestConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private AccessControlService accessControlService;

    @Test
    void unknownNonApiPath_isDenied() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/some/static/page.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void rootFaviconAndErrorPaths_arePublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isFound());
        mockMvc.perform(get("/favicon.ico")).andExpect(status().isNoContent());
    }

    @Test
    void swaggerPaths_arePermittedEvenWithoutSpringdocInSlice() throws Exception {
        // Không có springdoc trong slice test -> 404 (không phải 401) chứng tỏ đường dẫn được permitAll.
        mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void apiResponse_hasStrictCsp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.API_CSP))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void swaggerUi_hasRelaxedCsp() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.SWAGGER_UI_CSP));
    }

    @Test
    void hsts_onlySentOverHttps() throws Exception {
        mockMvc.perform(get("/api/v1/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"));
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
