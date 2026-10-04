package com.education.base.security;

import com.education.base.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giới hạn tần suất tổng quát: quy tắc theo đường dẫn / method / multipart, đếm theo IP và theo người dùng,
 * 429 + Retry-After, cấu hình tắt được.
 */
class RequestRateLimitFilterTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private SlidingWindowRateLimiterTest.MutableClock clock;
    private RateLimitProperties properties;

    @BeforeEach
    void setUp() {
        clock = new SlidingWindowRateLimiterTest.MutableClock();
        properties = new RateLimitProperties();
        Map<String, RateLimitProperties.Rule> rules = new LinkedHashMap<>();
        rules.put("change-password", rule(List.of("/api/v1/auth/change-password"), List.of("POST"), false,
                Duration.ofMinutes(15), 10, 2));
        rules.put("portal", rule(List.of("/api/v1/portal/**"), List.of(), false, Duration.ofMinutes(5), 0, 3));
        rules.put("upload", rule(List.of("/api/**"), List.of("post", "PUT"), true, Duration.ofHours(1), 3, 2));
        properties.setRules(rules);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static RateLimitProperties.Rule rule(List<String> paths, List<String> methods, boolean multipartOnly,
                                                 Duration window, int perIp, int perUser) {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setPaths(paths);
        rule.setMethods(methods);
        rule.setMultipartOnly(multipartOnly);
        rule.setWindow(window);
        rule.setPerIp(perIp);
        rule.setPerUser(perUser);
        return rule;
    }

    private RequestRateLimitFilter filter() {
        return new RequestRateLimitFilter(new SlidingWindowRateLimiter(clock, 1000), properties, objectMapper);
    }

    private static void loginAs(long userId) {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(userId).username("u" + userId).fullName("u" + userId)
                .roles(List.of("ROLE_STUDENT")).permissions(Set.of()).build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }

    private static MockHttpServletRequest request(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletResponse send(RequestRateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void changePassword_perUserBudget_returns429WithRetryAfter() throws Exception {
        RequestRateLimitFilter filter = filter();
        loginAs(5L);

        for (int i = 0; i < 2; i++) {
            assertThat(send(filter, request("POST", "/api/v1/auth/change-password", "10.0.0.1")).getStatus())
                    .isEqualTo(200);
        }
        MockHttpServletResponse rejected = send(filter, request("POST", "/api/v1/auth/change-password", "10.0.0.2"));

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(rejected.getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 15 * 60L);
        assertThat(rejected.getContentAsString()).contains("\"code\":\"TOO_MANY_REQUESTS\"");

        // Người dùng khác vẫn được; GET cùng đường dẫn không nằm trong quy tắc.
        loginAs(6L);
        assertThat(send(filter, request("POST", "/api/v1/auth/change-password", "10.0.0.2")).getStatus())
                .isEqualTo(200);
        loginAs(5L);
        assertThat(send(filter, request("GET", "/api/v1/auth/change-password", "10.0.0.2")).getStatus())
                .isEqualTo(200);
    }

    @Test
    void windowSlides_requestsAllowedAgainAfterWindow() throws Exception {
        RequestRateLimitFilter filter = filter();
        loginAs(5L);
        send(filter, request("POST", "/api/v1/auth/change-password", "10.0.0.1"));
        send(filter, request("POST", "/api/v1/auth/change-password", "10.0.0.1"));
        assertThat(send(filter, request("POST", "/api/v1/auth/change-password", "10.0.0.1")).getStatus())
                .isEqualTo(429);

        clock.advance(Duration.ofMinutes(16));

        assertThat(send(filter, request("POST", "/api/v1/auth/change-password", "10.0.0.1")).getStatus())
                .isEqualTo(200);
    }

    @Test
    void perIpBudget_appliesEvenWithoutLogin() throws Exception {
        properties.getRules().get("change-password").setPerIp(2);
        RequestRateLimitFilter filter = filter();

        send(filter, request("POST", "/api/v1/auth/change-password", "198.51.100.9"));
        send(filter, request("POST", "/api/v1/auth/change-password", "198.51.100.9"));

        assertThat(send(filter, request("POST", "/api/v1/auth/change-password", "198.51.100.9")).getStatus())
                .isEqualTo(429);
        assertThat(send(filter, request("POST", "/api/v1/auth/change-password", "198.51.100.10")).getStatus())
                .isEqualTo(200);
    }

    @Test
    void portal_defaultBudgetIsPerUser_notPerIp() throws Exception {
        RequestRateLimitFilter filter = filter();

        // Nhiều học sinh sau cùng một IP (NAT) không chặn nhau.
        for (long user = 100; user < 110; user++) {
            loginAs(user);
            assertThat(send(filter, request("GET", "/api/v1/portal/me/schedule", "203.0.113.1")).getStatus())
                    .isEqualTo(200);
        }
        loginAs(100L);
        send(filter, request("GET", "/api/v1/portal/me/grades", "203.0.113.1"));
        send(filter, request("POST", "/api/v1/portal/me/anything", "203.0.113.1"));
        assertThat(send(filter, request("GET", "/api/v1/portal/me", "203.0.113.1")).getStatus()).isEqualTo(429);
        // Ngoài /portal không bị ảnh hưởng.
        assertThat(send(filter, request("GET", "/api/v1/students", "203.0.113.1")).getStatus()).isEqualTo(200);
    }

    @Test
    void upload_onlyMultipartRequestsCount() throws Exception {
        RequestRateLimitFilter filter = filter();
        loginAs(9L);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest json = request("POST", "/api/v1/files", "10.0.0.5");
            json.setContentType("application/json");
            assertThat(send(filter, json).getStatus()).isEqualTo(200);
        }
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest upload = request("POST", "/api/v1/files/upload", "10.0.0.5");
            upload.setContentType("multipart/form-data; boundary=x");
            assertThat(send(filter, upload).getStatus()).isEqualTo(200);
        }
        MockHttpServletRequest third = request("PUT", "/api/v1/students/import", "10.0.0.5");
        third.setContentType("Multipart/Form-Data; boundary=y");
        assertThat(send(filter, third).getStatus()).isEqualTo(429);
    }

    @Test
    void disabledGlobally_orPerRule_letsEverythingThrough() throws Exception {
        properties.setEnabled(false);
        RequestRateLimitFilter off = filter();
        loginAs(5L);
        for (int i = 0; i < 5; i++) {
            assertThat(send(off, request("POST", "/api/v1/auth/change-password", "10.0.0.1")).getStatus())
                    .isEqualTo(200);
        }

        properties.setEnabled(true);
        properties.getRules().get("change-password").setEnabled(false);
        properties.getRules().get("portal").setPerUser(0);
        RequestRateLimitFilter partial = filter();
        assertThat(partial.activeRuleNames()).containsExactly("upload");
        for (int i = 0; i < 5; i++) {
            assertThat(send(partial, request("POST", "/api/v1/auth/change-password", "10.0.0.1")).getStatus())
                    .isEqualTo(200);
        }
    }

    @Test
    void preflightIsNeverCounted() throws Exception {
        RequestRateLimitFilter filter = filter();
        loginAs(5L);
        for (int i = 0; i < 5; i++) {
            assertThat(send(filter, request("OPTIONS", "/api/v1/auth/change-password", "10.0.0.1")).getStatus())
                    .isEqualTo(200);
        }
        assertThat(send(filter, request("POST", "/api/v1/auth/change-password", "10.0.0.1")).getStatus())
                .isEqualTo(200);
    }

    @Test
    void contextPathIsStripped() throws Exception {
        RequestRateLimitFilter filter = filter();
        loginAs(5L);
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = request("POST", "/edu/api/v1/auth/change-password", "10.0.0.1");
            request.setContextPath("/edu");
            send(filter, request);
        }
        MockHttpServletRequest request = request("POST", "/edu/api/v1/auth/change-password", "10.0.0.1");
        request.setContextPath("/edu");
        assertThat(send(filter, request).getStatus()).isEqualTo(429);
    }
}
