package com.education.base.security;

import com.education.base.config.AuthCookieProperties;
import com.education.base.service.AuthTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thuộc tính Set-Cookie của access / refresh token theo cấu hình {@code app.security.cookie}.
 */
class AuthCookieServiceTest {

    private static final AuthTokens TOKENS = AuthTokens.builder()
            .accessToken("acc").refreshToken("ref").accessTokenTtlSeconds(900).refreshTokenTtlSeconds(604_800)
            .build();

    private static List<String> write(boolean secure, String sameSite) {
        AuthCookieProperties properties = new AuthCookieProperties();
        properties.setSecure(secure);
        properties.setSameSite(sameSite);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthCookieService(properties).writeSession(new MockHttpServletRequest(), response, TOKENS);
        return response.getHeaders(HttpHeaders.SET_COOKIE);
    }

    @Test
    void secureStrictByDefault_withPathsAndMaxAgeMatchingTtl() {
        List<String> headers = write(true, "Strict");

        assertThat(headers).containsExactly(
                "EDU_ACCESS_TOKEN=acc; Path=/api; Max-Age=900; Expires=" + expires(headers.get(0))
                        + "; Secure; HttpOnly; SameSite=Strict",
                "EDU_REFRESH_TOKEN=ref; Path=/api/v1/auth; Max-Age=604800; Expires=" + expires(headers.get(1))
                        + "; Secure; HttpOnly; SameSite=Strict");
        assertThat(String.join("\n", headers)).doesNotContain("Domain=");
    }

    @Test
    void devOverHttp_omitsSecure_andLaxIsNormalized() {
        List<String> headers = write(false, "lax");

        assertThat(headers).allSatisfy(header -> assertThat(header)
                .doesNotContain("Secure")
                .contains("HttpOnly")
                .endsWith("SameSite=Lax"));
    }

    @Test
    void clearSession_expiresBothCookiesOnTheirOwnPaths() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthCookieService(new AuthCookieProperties()).clearSession(response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(2)
                .anySatisfy(h -> assertThat(h).startsWith("EDU_ACCESS_TOKEN=; Path=/api; Max-Age=0"))
                .anySatisfy(h -> assertThat(h).startsWith("EDU_REFRESH_TOKEN=; Path=/api/v1/auth; Max-Age=0"));
    }

    @Test
    void readsFirstNonBlankCookie_andIgnoresOversizedValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("EDU_ACCESS_TOKEN", " "), new Cookie("EDU_ACCESS_TOKEN", "tok"),
                new Cookie("EDU_REFRESH_TOKEN", "x".repeat(AuthCookieService.MAX_TOKEN_LENGTH + 1)));

        assertThat(AuthCookieService.readAccessToken(request)).contains("tok");
        assertThat(AuthCookieService.readRefreshToken(request)).isEmpty();
        assertThat(AuthCookieService.readAccessToken(new MockHttpServletRequest())).isEmpty();
    }

    private static String expires(String header) {
        int start = header.indexOf("Expires=") + "Expires=".length();
        return header.substring(start, header.indexOf(';', start));
    }
}
