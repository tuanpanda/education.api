package com.education.base.config;

import com.education.base.controller.HealthController;
import com.education.base.security.JwtTokenService;
import com.education.base.service.AccessControlService;
import com.education.base.support.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS đọc từ {@code app.cors.allowed-origins}: chỉ origin khai báo được phép, không bật credentials;
 * danh sách rỗng = chỉ same-origin.
 */
class CorsConfigTest {

    @Test
    void corsProperties_trimsAndDropsBlankEntries() {
        CorsProperties properties = new CorsProperties(
                Arrays.asList(" http://localhost:5173 ", "", "  ", null, "https://edu.example.vn", "http://localhost:5173"));

        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:5173", "https://edu.example.vn");
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
    }

    @Nested
    @WebMvcTest(HealthController.class)
    @Import(WebMvcSecurityTestConfig.class)
    @TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173, https://edu.example.vn")
    class ConfiguredOrigins {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private CorsProperties corsProperties;

        @MockBean
        private JwtTokenService jwtTokenService;

        @MockBean
        private AccessControlService accessControlService;

        @Test
        void commaSeparatedProperty_isBoundToList() {
            assertThat(corsProperties.allowedOrigins())
                    .isEqualTo(List.of("http://localhost:5173", "https://edu.example.vn"));
        }

        @Test
        void preflightFromAllowedOrigin_isAcceptedWithoutCredentials() throws Exception {
            mockMvc.perform(options("/api/v1/health")
                            .header(HttpHeaders.ORIGIN, "https://edu.example.vn")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://edu.example.vn"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void simpleRequestFromAllowedOrigin_exposesContentDisposition() throws Exception {
            mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void preflightFromUnknownOrigin_isRejected() throws Exception {
            mockMvc.perform(options("/api/v1/health")
                            .header(HttpHeaders.ORIGIN, "http://evil.example.com:8088")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void requestFromLanOriginNotConfigured_isRejected() throws Exception {
            // Trước đây pattern http://*:* cho phép mọi origin.
            mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, "http://192.168.1.50:8088"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Nested
    @WebMvcTest(HealthController.class)
    @Import(WebMvcSecurityTestConfig.class)
    @TestPropertySource(properties = "app.cors.allowed-origins=")
    class NoOriginsConfigured {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private CorsProperties corsProperties;

        @MockBean
        private JwtTokenService jwtTokenService;

        @MockBean
        private AccessControlService accessControlService;

        @Test
        void emptyProperty_meansNoOrigins() {
            assertThat(corsProperties.allowedOrigins()).isEmpty();
        }

        @Test
        void crossOriginRequest_getsNoCorsHeaders() throws Exception {
            mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void sameOriginRequest_stillWorks() throws Exception {
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }
}
