package com.education.base.security;

import com.education.base.controller.AuthController;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.service.AuthService;
import com.education.base.service.AuthTokens;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF thật (double-submit cookie {@code XSRF-TOKEN} + header {@code X-XSRF-TOKEN}): MockMvc dựng tay, KHÔNG có
 * customizer tự gắn CSRF của {@link WebMvcSecurityTestConfig}.
 */
@WebMvcTest(AuthController.class)
@Import(WebMvcSecurityTestConfig.class)
@TestPropertySource(properties = "app.security.cookie.secure=true")
class CsrfProtectionTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String LOGIN_BODY = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String fetchCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value(CSRF_HEADER))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String token = body.path("data").path("token").asText();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(token).isNotBlank();
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(token);
        return token;
    }

    private static MockHttpServletRequestBuilder login() {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY);
    }

    private void givenLoginSucceeds() {
        when(authService.login(any(LoginRequest.class))).thenReturn(AuthTokens.builder()
                .accessToken("a").refreshToken("r").accessTokenTtlSeconds(900).refreshTokenTtlSeconds(3600)
                .user(AuthUserResponse.builder().id(1L).username("admin").build())
                .build());
    }

    @Test
    void csrfEndpoint_returnsTokenAndSetsJsReadableCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CSRF_COOKIE))
                .andExpect(cookie().httpOnly(CSRF_COOKIE, false))
                .andExpect(cookie().path(CSRF_COOKIE, "/"))
                .andExpect(cookie().secure(CSRF_COOKIE, true))
                .andExpect(cookie().sameSite(CSRF_COOKIE, "Strict"));
        fetchCsrfToken();
    }

    @Test
    void login_withoutCsrfToken_isRejectedBeforeController() throws Exception {
        mockMvc.perform(login())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(RestAccessDeniedHandler.CSRF_TOKEN_INVALID));
        verifyNoInteractions(authService);
    }

    @Test
    void login_withHeaderButNoCookie_isRejected() throws Exception {
        mockMvc.perform(login().header(CSRF_HEADER, "guessed-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(RestAccessDeniedHandler.CSRF_TOKEN_INVALID));
        verifyNoInteractions(authService);
    }

    @Test
    void login_withMismatchedHeader_isRejected() throws Exception {
        String token = fetchCsrfToken();

        mockMvc.perform(login().cookie(new Cookie(CSRF_COOKIE, token)).header(CSRF_HEADER, token + "x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(RestAccessDeniedHandler.CSRF_TOKEN_INVALID));
        verifyNoInteractions(authService);
    }

    @Test
    void login_withMatchingCookieAndHeader_succeedsAndSetsSessionCookies() throws Exception {
        givenLoginSucceeds();
        String token = fetchCsrfToken();

        mockMvc.perform(login().cookie(new Cookie(CSRF_COOKIE, token)).header(CSRF_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(cookie().value(AuthCookieService.ACCESS_COOKIE, "a"))
                .andExpect(cookie().value(AuthCookieService.REFRESH_COOKIE, "r"));
        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void refreshAndLogout_requireCsrfToken() throws Exception {
        Cookie refresh = new Cookie(AuthCookieService.REFRESH_COOKIE, "refresh-token");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(RestAccessDeniedHandler.CSRF_TOKEN_INVALID));
        mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(RestAccessDeniedHandler.CSRF_TOKEN_INVALID));
        verifyNoInteractions(authService);

        String token = fetchCsrfToken();
        mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh, new Cookie(CSRF_COOKIE, token))
                        .header(CSRF_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_COOKIE, 0));
        verify(authService).logout(null, null, "refresh-token");
    }

    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER")
    void authenticatedWrite_withoutCsrfToken_isRejected_butReadsAreAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old@1234\",\"newPassword\":\"NewPass@456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(RestAccessDeniedHandler.CSRF_TOKEN_INVALID));
        verifyNoInteractions(authService);

        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isOk());
    }

    @Test
    void anonymous401_stillIssuesCsrfCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists(CSRF_COOKIE));
    }

    @Test
    void existingCsrfCookie_isNotRotatedOnEveryRequest() throws Exception {
        String token = fetchCsrfToken();

        mockMvc.perform(get("/api/v1/auth/csrf").cookie(new Cookie(CSRF_COOKIE, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(token))
                .andExpect(cookie().doesNotExist(CSRF_COOKIE));
    }

    /**
     * Phiên JWT không trạng thái: request đã xác thực KHÔNG được xóa / xoay cookie {@code XSRF-TOKEN}
     * (mặc định Spring gắn CsrfAuthenticationStrategy vào SessionManagementFilter, chạy ở MỌI request có JWT,
     * xóa cookie khiến POST kế tiếp bị 403 CSRF_TOKEN_INVALID).
     */
    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER")
    void authenticatedRequests_keepExistingCsrfCookie() throws Exception {
        when(authService.changePassword(any(), any())).thenReturn(AuthTokens.builder()
                .accessToken("a2").refreshToken("r2").accessTokenTtlSeconds(900).refreshTokenTtlSeconds(3600)
                .user(AuthUserResponse.builder().id(3L).username("teacher1").build())
                .build());
        String token = fetchCsrfToken();
        Cookie csrfCookie = new Cookie(CSRF_COOKIE, token);

        mockMvc.perform(get("/api/v1/auth/me").cookie(csrfCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(CSRF_COOKIE));

        mockMvc.perform(post("/api/v1/auth/change-password").cookie(csrfCookie).header(CSRF_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old@1234\",\"newPassword\":\"NewPass@456\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value(AuthCookieService.ACCESS_COOKIE, "a2"))
                .andExpect(cookie().doesNotExist(CSRF_COOKIE));

        mockMvc.perform(get("/api/v1/auth/csrf").cookie(csrfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(token))
                .andExpect(cookie().doesNotExist(CSRF_COOKIE));
    }
}
