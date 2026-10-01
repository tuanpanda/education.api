package com.education.base.controller;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.exception.UnauthorizedException;
import com.education.base.security.AuthCookieService;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.JwtAuthenticationFilter;
import com.education.base.security.JwtClaims;
import com.education.base.security.TokenType;
import com.education.base.service.AuthService;
import com.education.base.service.AuthTokens;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;

import static com.education.base.security.AuthCookieService.ACCESS_COOKIE;
import static com.education.base.security.AuthCookieService.REFRESH_COOKIE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đăng nhập / làm mới / đăng xuất / đổi mật khẩu bằng cookie HttpOnly: token chỉ nằm trong Set-Cookie (đúng thuộc
 * tính), body chỉ có thông tin người dùng.
 */
@WebMvcTest(AuthController.class)
@Import(WebMvcSecurityTestConfig.class)
@TestPropertySource(properties = {"app.security.cookie.secure=true", "app.security.cookie.same-site=Strict"})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private static AuthTokens tokens(boolean mustChange) {
        return AuthTokens.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .accessTokenTtlSeconds(900L)
                .refreshTokenTtlSeconds(604_800L)
                .user(AuthUserResponse.builder()
                        .id(1L).username("admin").fullName("Quản trị")
                        .roles(List.of("ROLE_ADMIN")).permissions(List.of("MENU_USER_LIST:VIEW"))
                        .mustChangePassword(mustChange)
                        .build())
                .build();
    }

    /** Cookie phiên mới: HttpOnly, Secure, SameSite=Strict, Path và Max-Age đúng thời hạn token. */
    private static void expectSessionCookies(ResultActions result) throws Exception {
        result
                .andExpect(cookie().value(ACCESS_COOKIE, "access-token"))
                .andExpect(cookie().httpOnly(ACCESS_COOKIE, true))
                .andExpect(cookie().secure(ACCESS_COOKIE, true))
                .andExpect(cookie().path(ACCESS_COOKIE, "/api"))
                .andExpect(cookie().maxAge(ACCESS_COOKIE, 900))
                .andExpect(cookie().sameSite(ACCESS_COOKIE, "Strict"))
                .andExpect(cookie().value(REFRESH_COOKIE, "refresh-token"))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(cookie().secure(REFRESH_COOKIE, true))
                .andExpect(cookie().path(REFRESH_COOKIE, "/api/v1/auth"))
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 604_800))
                .andExpect(cookie().sameSite(REFRESH_COOKIE, "Strict"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    private static ResultMatcher[] cookiesCleared() {
        return new ResultMatcher[]{
                cookie().maxAge(ACCESS_COOKIE, 0),
                cookie().path(ACCESS_COOKIE, "/api"),
                cookie().httpOnly(ACCESS_COOKIE, true),
                cookie().maxAge(REFRESH_COOKIE, 0),
                cookie().path(REFRESH_COOKIE, "/api/v1/auth"),
                cookie().httpOnly(REFRESH_COOKIE, true)
        };
    }

    /** Body tuyệt đối không chứa token (JavaScript không đọc được token nữa). */
    private static ResultMatcher[] noTokensInBody() {
        return new ResultMatcher[]{
                jsonPath("$.data.accessToken").doesNotExist(),
                jsonPath("$.data.refreshToken").doesNotExist(),
                jsonPath("$.data.tokenType").doesNotExist(),
                jsonPath("$..accessToken").isEmpty(),
                jsonPath("$..refreshToken").isEmpty()
        };
    }

    @Test
    void login_setsHttpOnlyCookiesAndReturnsUserWithoutTokens() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(tokens(true));

        ResultActions result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.data.permissions[0]").value("MENU_USER_LIST:VIEW"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpectAll(noTokensInBody())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        expectSessionCookies(result);
    }

    @Test
    void login_missingPassword_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(authService);
    }

    @Test
    void login_badCredentials_returns401WithoutCookies() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(cookie().doesNotExist(ACCESS_COOKIE))
                .andExpect(cookie().doesNotExist(REFRESH_COOKIE));
    }

    @Test
    void login_temporarilyLocked_returns401WithDistinctCode() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new UnauthorizedException(
                "ACCOUNT_TEMPORARILY_LOCKED",
                "Tài khoản tạm thời bị khóa do nhập sai mật khẩu nhiều lần. Vui lòng thử lại sau 15 phút."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_TEMPORARILY_LOCKED"))
                .andExpect(jsonPath("$.message").value(containsString("15 phút")));
    }

    @Test
    void refresh_readsRefreshCookie_andSetsRotatedCookies() throws Exception {
        when(authService.refresh("refresh-old")).thenReturn(tokens(false));

        ResultActions result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, "refresh-old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpectAll(noTokensInBody());
        expectSessionCookies(result);
        verify(authService).refresh("refresh-old");
    }

    @Test
    void refresh_withoutCookie_returns401AndClearsCookies_evenIfBodyHasToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andExpectAll(cookiesCleared());
        verifyNoInteractions(authService);
    }

    @Test
    void refresh_rejectedToken_returns401AndClearsCookies() throws Exception {
        when(authService.refresh("reused")).thenThrow(new UnauthorizedException("REFRESH_TOKEN_REUSED",
                "Phát hiện phiên đăng nhập bị dùng lại bất thường."));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, "reused")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"))
                .andExpectAll(cookiesCleared());
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER", mustChangePassword = true)
    void me_allowedWhilePasswordChangePending() throws Exception {
        when(authService.me(any(AuthUserPrincipal.class))).thenReturn(tokens(true).getUser());

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER", mustChangePassword = true)
    void changePassword_usesCurrentUser_andSetsCookiesOfNewSession() throws Exception {
        when(authService.changePassword(eq(3L), any(ChangePasswordRequest.class))).thenReturn(tokens(false));

        ResultActions result = mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("Old@1234", "NewPass@456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpectAll(noTokensInBody());
        expectSessionCookies(result);
        verify(authService).changePassword(eq(3L), any(ChangePasswordRequest.class));
    }

    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER")
    void changePassword_weakPassword_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old@1234\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER")
    void changePassword_over72Utf8Bytes_returnsValidationError() throws Exception {
        String tooLong = "a1" + "x".repeat(69) + "ă"; // 72 ký tự nhưng 73 byte UTF-8
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("Old@1234", tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old@1234\",\"newPassword\":\"NewPass@456\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER")
    void logout_authenticated_revokesBySessionAndRefreshCookie_andClearsCookies() throws Exception {
        JwtClaims claims = new JwtClaims(3L, "teacher1", 0, TokenType.ACCESS, null, "jti", "sid-9");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .requestAttr(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE, claims)
                        .cookie(new Cookie(REFRESH_COOKIE, "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpectAll(cookiesCleared());
        verify(authService).logout(3L, "sid-9", "refresh-token");
    }

    @Test
    void logout_withExpiredAccessToken_stillRevokesByRefreshCookie_andClearsCookies() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE, "refresh-token")))
                .andExpect(status().isOk())
                .andExpectAll(cookiesCleared());
        verify(authService).logout(null, null, "refresh-token");
    }

    @Test
    void logout_withoutAnyCookie_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpectAll(cookiesCleared());
        verify(authService).logout(null, null, null);
    }

    @Test
    void logout_ignoresLegacyBodyToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"from-body\"}"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, not(hasItem(containsString("from-body")))));
        verify(authService).logout(null, null, null);
    }

    @Test
    void accessCookieName_isStable() {
        // Frontend / nginx không phụ thuộc tên cookie, nhưng đổi tên sẽ buộc mọi người đăng nhập lại.
        org.assertj.core.api.Assertions.assertThat(AuthCookieService.ACCESS_COOKIE).isEqualTo("EDU_ACCESS_TOKEN");
        org.assertj.core.api.Assertions.assertThat(AuthCookieService.REFRESH_COOKIE).isEqualTo("EDU_REFRESH_TOKEN");
    }
}
