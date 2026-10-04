package com.education.base.security;

import com.education.base.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Giới hạn tần suất tổng quát theo quy tắc {@link RateLimitProperties} (đổi mật khẩu, upload, ngân sách mặc định
 * cho {@code /api/v1/portal/**}...): theo IP và theo người dùng đã đăng nhập. Vượt ngưỡng trả HTTP 429
 * {@value AuthRateLimitFilter#TOO_MANY_REQUESTS} kèm {@code Retry-After}.
 * <p>
 * Đăng ký SAU chuỗi Spring Security (như {@link AuthRateLimitFilter}): phản hồi 429 vẫn có header CORS, và
 * {@code SecurityContext} đã có người dùng (request chưa đăng nhập tới {@code /api/**} bị chặn 401 trước đó).
 * IP lấy từ {@link HttpServletRequest#getRemoteAddr()} (sau reverse proxy cần
 * {@code server.forward-headers-strategy=native}).
 */
@Slf4j
public class RequestRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_KEY_LENGTH = 100;

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final SecurityErrorWriter errorWriter;
    private final List<CompiledRule> rules;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RequestRateLimitFilter(RateLimiter limiter, RateLimitProperties properties, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.properties = properties;
        this.errorWriter = new SecurityErrorWriter(objectMapper);
        this.rules = compile(properties.getRules());
        if (properties.isEnabled()) {
            rules.forEach(rule -> log.info("Rate limit '{}': paths={}, methods={}, multipartOnly={}, window={}, "
                            + "perIp={}, perUser={}", rule.name(), rule.paths(),
                    rule.methods().isEmpty() ? "*" : rule.methods(), rule.multipartOnly(), rule.rule().getWindow(),
                    rule.rule().getPerIp(), rule.rule().getPerUser()));
        }
    }

    /** Các quy tắc đang hiệu lực (đã bỏ quy tắc tắt / không có đường dẫn / không có ngưỡng). */
    public List<String> activeRuleNames() {
        return rules.stream().map(CompiledRule::name).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || rules.isEmpty() || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathWithinApplication(request);
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        boolean multipart = isMultipart(request);
        Long userId = SecurityUtils.currentUser().map(AuthUserPrincipal::getId).orElse(null);
        String ip = truncate(request.getRemoteAddr());

        for (CompiledRule rule : rules) {
            if (!rule.matches(matcher, path, method, multipart)) {
                continue;
            }
            RateLimitProperties.Rule config = rule.rule();
            if (config.getPerIp() > 0) {
                RateLimiter.Decision byIp = limiter.tryAcquire(
                        "rl:" + rule.name() + ":ip:" + ip, config.getPerIp(), config.getWindow());
                if (!byIp.allowed()) {
                    log.warn("Vượt giới hạn tần suất '{}' theo IP: ip={}, path={}", rule.name(), ip, path);
                    reject(response, byIp);
                    return;
                }
            }
            if (config.getPerUser() > 0 && userId != null) {
                RateLimiter.Decision byUser = limiter.tryAcquire(
                        "rl:" + rule.name() + ":user:" + userId, config.getPerUser(), config.getWindow());
                if (!byUser.allowed()) {
                    log.warn("Vượt giới hạn tần suất '{}' theo người dùng: userId={}, ip={}, path={}",
                            rule.name(), userId, ip, path);
                    reject(response, byUser);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, RateLimiter.Decision decision) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(), AuthRateLimitFilter.TOO_MANY_REQUESTS,
                "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau "
                        + AuthRateLimitFilter.describeWait(decision.retryAfterSeconds()) + ".");
    }

    static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= MAX_KEY_LENGTH ? value : value.substring(0, MAX_KEY_LENGTH);
    }

    private static List<CompiledRule> compile(Map<String, RateLimitProperties.Rule> configured) {
        List<CompiledRule> out = new ArrayList<>();
        if (configured == null) {
            return out;
        }
        configured.forEach((name, rule) -> {
            if (rule == null || !rule.isEnabled() || rule.getPaths() == null || rule.getPaths().isEmpty()
                    || (rule.getPerIp() <= 0 && rule.getPerUser() <= 0) || rule.getWindow() == null
                    || rule.getWindow().isZero() || rule.getWindow().isNegative()) {
                return;
            }
            Set<String> methods = rule.getMethods() == null ? Set.of() : rule.getMethods().stream()
                    .filter(m -> m != null && !m.isBlank())
                    .map(m -> m.trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            List<String> paths = rule.getPaths().stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(String::trim)
                    .toList();
            if (!paths.isEmpty()) {
                out.add(new CompiledRule(name, paths, methods, rule.isMultipartOnly(), rule));
            }
        });
        return List.copyOf(out);
    }

    private record CompiledRule(String name, List<String> paths, Set<String> methods, boolean multipartOnly,
                                RateLimitProperties.Rule rule) {

        boolean matches(AntPathMatcher matcher, String path, String method, boolean multipart) {
            if (multipartOnly && !multipart) {
                return false;
            }
            if (!methods.isEmpty() && !methods.contains(method)) {
                return false;
            }
            for (String pattern : paths) {
                if (matcher.match(pattern, path)) {
                    return true;
                }
            }
            return false;
        }
    }
}
