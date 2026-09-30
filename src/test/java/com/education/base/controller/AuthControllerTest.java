package com.education.base.controller;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.response.AuthTokenResponse;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.exception.UnauthorizedException;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.JwtAuthenticationFilter;
import com.education.base.security.JwtClaims;
import com.education.base.security.TokenType;
import com.education.base.service.AuthService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(WebMvcSecurityTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private static AuthTokenResponse tokens(boolean mustChange) {
        return AuthTokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(AuthUserResponse.builder()
                        .id(1L).username("admin").fullName("Quản trị")
                        .roles(List.of("ROLE_ADMIN")).permissions(List.of("MENU_USER_LIST:VIEW"))
                        .mustChangePassword(mustChange)
                        .build())
                .build();
    }

    @Test
    void login_isPublicAndReturnsTokens() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(tokens(true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.data.user.permissions[0]").value("MENU_USER_LIST:VIEW"))
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
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
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refresh_isPublic() throws Exception {
        when(authService.refresh(any())).thenReturn(tokens(false));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
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
    void changePassword_usesCurrentUser() throws Exception {
        when(authService.changePassword(eq(3L), any(ChangePasswordRequest.class))).thenReturn(tokens(false));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("Old@1234", "NewPass@456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(false));
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
    void login_temporarilyLocked_returns401WithDistinctCode() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new UnauthorizedException(
                "ACCOUNT_TEMPORARILY_LOCKED",
                "Tài khoản tạm thời bị khóa do nhập sai mật khẩu nhiều lần. Vui lòng thử lại sau 15 phút."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_TEMPORARILY_LOCKED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("15 phút")));
    }

    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER")
    void logout_withoutBody_isBackwardCompatible() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"));
        verify(authService).logout(3L, null, null);
    }

    @Test
    @WithAuthUser(id = 3L, username = "teacher1", roles = "ROLE_TEACHER")
    void logout_usesSessionOfAccessTokenAndOptionalRefreshToken() throws Exception {
        JwtClaims claims = new JwtClaims(3L, "teacher1", 0, TokenType.ACCESS, null, "jti", "sid-9");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .requestAttr(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE, claims)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk());
        verify(authService).logout(3L, "sid-9", "refresh-token");
    }

    @Test
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }
}
