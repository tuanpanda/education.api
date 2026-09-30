package com.education.base.security;

import com.education.base.config.AuthSecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Giới hạn tần suất {@code POST /api/v1/auth/login} và {@code POST /api/v1/auth/refresh} theo IP và theo
 * tài khoản (tên đăng nhập / người dùng của refresh token). Vượt ngưỡng trả HTTP 429 kèm {@code Retry-After}.
 * <p>
 * Được đăng ký SAU chuỗi Spring Security (để phản hồi 429 vẫn có header CORS) - xem {@code AuthSecurityConfig}.
 * IP lấy từ {@link HttpServletRequest#getRemoteAddr()}; phía sau reverse proxy (nginx của UI) cần bật
 * {@code server.forward-headers-strategy} để Tomcat lấy IP thật từ {@code X-Forwarded-For}.
 */
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    public static final String LOGIN_PATH = "/api/v1/auth/login";
    public static final String REFRESH_PATH = "/api/v1/auth/refresh";
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";

    /** Chỉ đọc tối đa ngần này byte đầu của body để lấy tên đăng nhập / refresh token. */
    static final int MAX_INSPECTED_BODY_BYTES = 16 * 1024;
    private static final int MAX_KEY_LENGTH = 100;

    private final SlidingWindowRateLimiter limiter;
    private final AuthSecurityProperties.RateLimit config;
    private final ObjectMapper objectMapper;
    private final JwtTokenService jwtTokenService;
    private final SecurityErrorWriter errorWriter;

    public AuthRateLimitFilter(SlidingWindowRateLimiter limiter, AuthSecurityProperties.RateLimit config,
                               ObjectMapper objectMapper, JwtTokenService jwtTokenService) {
        this.limiter = limiter;
        this.config = config;
        this.objectMapper = objectMapper;
        this.jwtTokenService = jwtTokenService;
        this.errorWriter = new SecurityErrorWriter(objectMapper);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.isEnabled()
                || !HttpMethod.POST.matches(request.getMethod())
                || endpoint(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String endpoint = endpoint(request);
        boolean login = LOGIN_PATH.equals(endpoint);
        String ip = truncate(request.getRemoteAddr());

        SlidingWindowRateLimiter.Decision byIp = limiter.tryAcquire(
                (login ? "login:ip:" : "refresh:ip:") + ip,
                login ? config.getLoginPerIp() : config.getRefreshPerIp(),
                config.getWindow());
        if (!byIp.allowed()) {
            log.warn("Vượt giới hạn tần suất {} theo IP: ip={}", endpoint, ip);
            reject(response, byIp);
            return;
        }

        CachedBodyRequest wrapped = new CachedBodyRequest(request);
        String accountKey = login ? loginAccountKey(wrapped) : refreshAccountKey(wrapped);
        if (accountKey != null) {
            SlidingWindowRateLimiter.Decision byAccount = limiter.tryAcquire(
                    accountKey,
                    login ? config.getLoginPerUsername() : config.getRefreshPerUser(),
                    config.getWindow());
            if (!byAccount.allowed()) {
                log.warn("Vượt giới hạn tần suất {} theo tài khoản: key={}, ip={}", endpoint, accountKey, ip);
                reject(response, byAccount);
                return;
            }
        }
        chain.doFilter(wrapped, response);
    }

    private void reject(HttpServletResponse response, SlidingWindowRateLimiter.Decision decision) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(), TOO_MANY_REQUESTS,
                "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau " + describeWait(decision.retryAfterSeconds()) + ".");
    }

    static String describeWait(long seconds) {
        if (seconds < 60) {
            return seconds + " giây";
        }
        return ((seconds + 59) / 60) + " phút";
    }

    private String loginAccountKey(CachedBodyRequest request) {
        JsonNode body = request.jsonBody(objectMapper);
        if (body == null) {
            return null;
        }
        JsonNode username = body.get("username");
        if (username == null || !username.isTextual() || username.asText().isBlank()) {
            return null;
        }
        return "login:user:" + truncate(username.asText().trim().toLowerCase(Locale.ROOT));
    }

    private String refreshAccountKey(CachedBodyRequest request) {
        JsonNode body = request.jsonBody(objectMapper);
        if (body == null || jwtTokenService == null) {
            return null;
        }
        JsonNode token = body.get("refreshToken");
        if (token == null || !token.isTextual() || token.asText().isBlank()) {
            return null;
        }
        try {
            // Chỉ tin người dùng của token có chữ ký hợp lệ (không để kẻ gian chặn tài khoản khác).
            JwtClaims claims = jwtTokenService.parse(token.asText(), TokenType.REFRESH);
            return claims == null || claims.userId() == null ? null : "refresh:user:" + claims.userId();
        } catch (InvalidTokenException ex) {
            return null;
        }
    }

    private static String endpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return null;
        }
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
        if (LOGIN_PATH.equals(path)) {
            return LOGIN_PATH;
        }
        if (REFRESH_PATH.equals(path)) {
            return REFRESH_PATH;
        }
        return null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= MAX_KEY_LENGTH ? value : value.substring(0, MAX_KEY_LENGTH);
    }

    /**
     * Đọc trước tối đa {@link #MAX_INSPECTED_BODY_BYTES} byte của body để phân tích, sau đó phát lại
     * nguyên vẹn (phần đã đọc + phần còn lại) cho controller.
     */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private byte[] head;
        private boolean complete;
        private boolean streamOpened;
        private InputStream replay;

        CachedBodyRequest(HttpServletRequest request) {
            super(request);
        }

        JsonNode jsonBody(ObjectMapper objectMapper) {
            try {
                readHead();
                if (!complete || head.length == 0) {
                    return null;
                }
                return objectMapper.readTree(head);
            } catch (IOException | RuntimeException ex) {
                return null;
            }
        }

        private void readHead() throws IOException {
            if (head != null) {
                return;
            }
            InputStream original = super.getInputStream();
            head = original.readNBytes(MAX_INSPECTED_BODY_BYTES + 1);
            complete = head.length <= MAX_INSPECTED_BODY_BYTES;
            replay = new SequenceInputStream(new ByteArrayInputStream(head), original);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (head == null) {
                return super.getInputStream();
            }
            if (streamOpened) {
                throw new IllegalStateException("Body của request đã được đọc.");
            }
            streamOpened = true;
            InputStream source = replay;
            return new ServletInputStream() {
                private boolean finished;

                @Override
                public int read() throws IOException {
                    int value = source.read();
                    finished = value < 0;
                    return value;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int count = source.read(b, off, len);
                    finished = count < 0;
                    return count;
                }

                @Override
                public boolean isFinished() {
                    return finished;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Không hỗ trợ đọc bất đồng bộ.");
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
