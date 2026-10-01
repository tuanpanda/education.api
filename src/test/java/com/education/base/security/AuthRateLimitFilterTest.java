package com.education.base.security;

import com.education.base.config.AuthSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Giới hạn tần suất đăng nhập / làm mới token theo IP và theo tài khoản.
 */
class AuthRateLimitFilterTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private SlidingWindowRateLimiterTest.MutableClock clock;
    private AuthSecurityProperties.RateLimit config;
    private JwtTokenService jwtTokenService;
    private AuthRateLimitFilter filter;
    private List<String> forwardedBodies;

    @BeforeEach
    void setUp() {
        clock = new SlidingWindowRateLimiterTest.MutableClock();
        config = new AuthSecurityProperties.RateLimit();
        config.setWindow(Duration.ofMinutes(5));
        config.setLoginPerIp(5);
        config.setLoginPerUsername(3);
        config.setRefreshPerIp(4);
        config.setRefreshPerUser(2);
        jwtTokenService = Mockito.mock(JwtTokenService.class);
        filter = new AuthRateLimitFilter(new SlidingWindowRateLimiter(clock, 1000), config, objectMapper,
                jwtTokenService);
        forwardedBodies = new ArrayList<>();
    }

    private MockHttpServletResponse login(String ip, String username) throws Exception {
        return post(AuthRateLimitFilter.LOGIN_PATH, ip,
                "{\"username\":\"" + username + "\",\"password\":\"Secret@123\"}");
    }

    private MockHttpServletResponse refresh(String ip, String refreshCookie) throws Exception {
        return post(AuthRateLimitFilter.REFRESH_PATH, ip, "",
                new Cookie(AuthCookieService.REFRESH_COOKIE, refreshCookie));
    }

    private MockHttpServletResponse post(String path, String ip, String body, Cookie... cookies) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (cookies.length > 0) {
            request.setCookies(cookies);
        }
        request.setRemoteAddr(ip);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (ServletRequest req, jakarta.servlet.ServletResponse res) ->
                forwardedBodies.add(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void login_perUsernameLimit_returns429AndIsCaseInsensitive() throws Exception {
        assertThat(login("10.0.0.1", "teacher1").getStatus()).isEqualTo(200);
        assertThat(login("10.0.0.2", "Teacher1").getStatus()).isEqualTo(200);
        assertThat(login("10.0.0.3", " TEACHER1 ").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = login("10.0.0.4", "teacher1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("300");
        assertThat(blocked.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"TOO_MANY_REQUESTS\"")
                .contains("Vui lòng thử lại sau 5 phút");
        // Tài khoản khác vẫn đăng nhập được.
        assertThat(login("10.0.0.4", "teacher2").getStatus()).isEqualTo(200);
    }

    @Test
    void login_perIpLimit_returns429() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login("10.0.0.9", "user" + i).getStatus()).isEqualTo(200);
        }
        assertThat(login("10.0.0.9", "someone-else").getStatus()).isEqualTo(429);
        assertThat(login("10.0.0.10", "someone-else").getStatus()).isEqualTo(200);
    }

    @Test
    void login_windowSlides_allowsAgain() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("10.0.0.1", "teacher1");
        }
        assertThat(login("10.0.0.1", "teacher1").getStatus()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(login("10.0.0.1", "teacher1").getStatus()).isEqualTo(200);
    }

    @Test
    void login_bodyIsForwardedIntactToController() throws Exception {
        login("10.0.0.1", "teacher1");

        assertThat(forwardedBodies).containsExactly("{\"username\":\"teacher1\",\"password\":\"Secret@123\"}");
    }

    @Test
    void refresh_perUserLimit_usesVerifiedTokenOwner() throws Exception {
        when(jwtTokenService.parse(eq("r-good"), eq(TokenType.REFRESH)))
                .thenReturn(new JwtClaims(7L, "teacher1", 0, TokenType.REFRESH, null, "jti", "sid"));

        assertThat(refresh("10.0.0.1", "r-good").getStatus()).isEqualTo(200);
        assertThat(refresh("10.0.0.2", "r-good").getStatus()).isEqualTo(200);
        assertThat(refresh("10.0.0.3", "r-good").getStatus()).isEqualTo(429);
    }

    @Test
    void refresh_invalidToken_onlyCountsPerIp() throws Exception {
        when(jwtTokenService.parse(anyString(), eq(TokenType.REFRESH)))
                .thenThrow(new InvalidTokenException(InvalidTokenException.TOKEN_INVALID, "bad"));

        for (int i = 0; i < 4; i++) {
            assertThat(refresh("10.0.0.5", "forged").getStatus()).isEqualTo(200);
        }
        assertThat(refresh("10.0.0.5", "forged").getStatus()).isEqualTo(429);
        assertThat(refresh("10.0.0.6", "forged").getStatus()).isEqualTo(200);
    }

    @Test
    void refresh_tokenInBodyIsIgnored_onlyCookieCountsPerUser() throws Exception {
        String body = "{\"refreshToken\":\"r-good\"}";

        for (int i = 0; i < 4; i++) {
            assertThat(post(AuthRateLimitFilter.REFRESH_PATH, "10.0.1." + i, body).getStatus()).isEqualTo(200);
        }
        org.mockito.Mockito.verifyNoInteractions(jwtTokenService);
    }

    @Test
    void otherPathsAndMethods_andDisabledConfig_areNotLimited() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", AuthRateLimitFilter.LOGIN_PATH);
        MockHttpServletRequest logout = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        assertThat(filter.shouldNotFilter(get)).isTrue();
        assertThat(filter.shouldNotFilter(logout)).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", AuthRateLimitFilter.LOGIN_PATH)))
                .isFalse();

        config.setEnabled(false);
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", AuthRateLimitFilter.LOGIN_PATH)))
                .isTrue();
    }

    @Test
    void describeWait_usesSecondsOrMinutes() {
        assertThat(AuthRateLimitFilter.describeWait(30)).isEqualTo("30 giây");
        assertThat(AuthRateLimitFilter.describeWait(61)).isEqualTo("2 phút");
    }
}
