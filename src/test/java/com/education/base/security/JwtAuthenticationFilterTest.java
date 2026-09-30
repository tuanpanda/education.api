package com.education.base.security;

import com.education.base.service.AccessControlService;
import com.education.base.service.RefreshTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Access token gắn phiên ({@code sid}) bị từ chối ngay khi phiên đã đăng xuất / bị thu hồi.
 */
class JwtAuthenticationFilterTest {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtTokenService, accessControlService, refreshTokenService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        return request;
    }

    private void givenToken(String sessionId) {
        when(jwtTokenService.parse("token", TokenType.ACCESS))
                .thenReturn(new JwtClaims(7L, "teacher1", 2, TokenType.ACCESS, null, "jti", sessionId));
        when(accessControlService.loadActivePrincipal(7L)).thenReturn(Optional.of(
                AuthUserPrincipal.builder().id(7L).username("teacher1").role("ROLE_TEACHER").tokenVersion(2).build()));
    }

    @Test
    void activeSession_authenticatesAndExposesClaims() throws Exception {
        givenToken("sid-1");
        when(refreshTokenService.isSessionActive("sid-1")).thenReturn(true);
        MockHttpServletRequest request = request();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE)).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE))
                .isInstanceOfSatisfying(JwtClaims.class, c -> assertThat(c.sessionId()).isEqualTo("sid-1"));
    }

    @Test
    void revokedSession_isRejectedAsTokenRevoked() throws Exception {
        givenToken("sid-1");
        when(refreshTokenService.isSessionActive("sid-1")).thenReturn(false);
        MockHttpServletRequest request = request();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(JwtAuthenticationFilter.TOKEN_REVOKED));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void legacyTokenWithoutSession_skipsSessionCheck() throws Exception {
        givenToken(null);
        MockHttpServletRequest request = request();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE)).isNull();
        verify(refreshTokenService, never()).isSessionActive(org.mockito.ArgumentMatchers.any());
    }
}
