package com.education.base.controller;

import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.InvalidTokenException;
import com.education.base.security.JwtClaims;
import com.education.base.security.JwtTokenService;
import com.education.base.security.TokenType;
import com.education.base.service.AccessControlService;
import com.education.base.support.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra filter JWT thật trong chuỗi Spring Security (token hợp lệ / hết hạn / bị thu hồi, CORS preflight).
 */
@WebMvcTest(MenuController.class)
@Import(WebMvcSecurityTestConfig.class)
class JwtSecurityFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private AccessControlService accessControlService;

    private static AuthUserPrincipal principal(int version) {
        return AuthUserPrincipal.builder().id(7L).username("teacher1").role("ROLE_TEACHER")
                .permission("MENU_STUDENT_LIST:VIEW").tokenVersion(version).build();
    }

    @Test
    void validBearerToken_authenticatesRequest() throws Exception {
        when(jwtTokenService.parse("good", TokenType.ACCESS))
                .thenReturn(new JwtClaims(7L, "teacher1", 2, TokenType.ACCESS, null));
        when(accessControlService.loadActivePrincipal(7L)).thenReturn(Optional.of(principal(2)));
        when(accessControlService.getNavigation(any())).thenReturn(UserNavigationResponseDto.builder()
                .menus(List.of()).permissions(new LinkedHashSet<>(List.of("MENU_STUDENT_LIST:VIEW"))).build());

        mockMvc.perform(get("/api/v1/menus/user-navigation").header(HttpHeaders.AUTHORIZATION, "Bearer good"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("MENU_STUDENT_LIST:VIEW"));
    }

    @Test
    void expiredToken_returns401WithExpiredCode() throws Exception {
        when(jwtTokenService.parse("old", TokenType.ACCESS))
                .thenThrow(new InvalidTokenException(InvalidTokenException.TOKEN_EXPIRED, "Phiên đăng nhập đã hết hạn."));

        mockMvc.perform(get("/api/v1/menus/user-navigation").header(HttpHeaders.AUTHORIZATION, "Bearer old"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void revokedTokenVersion_returns401() throws Exception {
        when(jwtTokenService.parse("stale", TokenType.ACCESS))
                .thenReturn(new JwtClaims(7L, "teacher1", 1, TokenType.ACCESS, null));
        when(accessControlService.loadActivePrincipal(7L)).thenReturn(Optional.of(principal(2)));

        mockMvc.perform(get("/api/v1/menus/user-navigation").header(HttpHeaders.AUTHORIZATION, "Bearer stale"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void lockedOrDeletedUser_returns401() throws Exception {
        when(jwtTokenService.parse("locked", TokenType.ACCESS))
                .thenReturn(new JwtClaims(7L, "teacher1", 2, TokenType.ACCESS, null));
        when(accessControlService.loadActivePrincipal(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/menus/user-navigation").header(HttpHeaders.AUTHORIZATION, "Bearer locked"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void corsPreflight_isAllowedWithoutToken() throws Exception {
        mockMvc.perform(options("/api/v1/menus/user-navigation")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void swaggerDocs_arePublic() throws Exception {
        // Không có springdoc trong slice test -> 404 (không phải 401) chứng tỏ đường dẫn được permitAll.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
