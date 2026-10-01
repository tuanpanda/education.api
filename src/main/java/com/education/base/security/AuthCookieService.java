package com.education.base.security;

import com.education.base.config.AuthCookieProperties;
import com.education.base.service.AuthTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ghi / xóa / đọc cookie phiên đăng nhập. Token KHÔNG còn nằm trong body phản hồi hay localStorage:
 * <ul>
 *     <li>{@value #ACCESS_COOKIE}: access token, {@code HttpOnly}, {@code Path=}{@value #ACCESS_PATH},
 *     {@code Max-Age} = thời hạn access token.</li>
 *     <li>{@value #REFRESH_COOKIE}: refresh token, {@code HttpOnly}, {@code Path=}{@value #REFRESH_PATH}
 *     (chỉ gửi tới API xác thực: refresh, logout, ...), {@code Max-Age} = thời hạn refresh token.</li>
 * </ul>
 * Cả hai cookie là host-only (không đặt {@code Domain}), {@code SameSite} và {@code Secure} theo
 * {@link AuthCookieProperties}. Không khai báo {@code @Component}: {@code SecurityConfig} tạo bean.
 */
@Slf4j
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "EDU_ACCESS_TOKEN";
    public static final String REFRESH_COOKIE = "EDU_REFRESH_TOKEN";
    /** Mọi API đều nằm dưới {@code /api}: trình duyệt không gửi access token cho trang tĩnh / Swagger UI. */
    public static final String ACCESS_PATH = "/api";
    /** Refresh token chỉ đi tới {@code /api/v1/auth/**} (refresh, logout); các API nghiệp vụ không nhận được. */
    public static final String REFRESH_PATH = "/api/v1/auth";

    /** JWT hợp lệ ngắn hơn nhiều; cookie dài hơn coi như rác, không đem đi phân tích. */
    static final int MAX_TOKEN_LENGTH = 8192;

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private final AuthCookieProperties properties;
    private final AtomicBoolean insecureWarningLogged = new AtomicBoolean();

    public AuthCookieService(AuthCookieProperties properties) {
        this.properties = properties;
    }

    /** Đặt cặp cookie mới sau khi đăng nhập / làm mới / đổi mật khẩu. */
    public void writeSession(HttpServletRequest request, HttpServletResponse response, AuthTokens tokens) {
        warnIfSecureCookieOverHttp(request);
        add(response, cookie(ACCESS_COOKIE, tokens.getAccessToken(), ACCESS_PATH,
                Duration.ofSeconds(tokens.getAccessTokenTtlSeconds())));
        add(response, cookie(REFRESH_COOKIE, tokens.getRefreshToken(), REFRESH_PATH,
                Duration.ofSeconds(tokens.getRefreshTokenTtlSeconds())));
    }

    /** Xóa cả hai cookie ({@code Max-Age=0}, cùng Path / thuộc tính như lúc đặt). */
    public void clearSession(HttpServletResponse response) {
        add(response, cookie(ACCESS_COOKIE, "", ACCESS_PATH, Duration.ZERO));
        add(response, cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO));
    }

    public static Optional<String> readAccessToken(HttpServletRequest request) {
        return read(request, ACCESS_COOKIE);
    }

    public static Optional<String> readRefreshToken(HttpServletRequest request) {
        return read(request, REFRESH_COOKIE);
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        // Trình duyệt gửi cookie có Path cụ thể hơn trước: lấy giá trị đầu tiên khác rỗng.
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue() == null ? "" : cookie.getValue().trim();
                if (!value.isEmpty() && value.length() <= MAX_TOKEN_LENGTH) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.normalizedSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Cookie {@code Secure} đặt qua HTTP thuần (trừ localhost) sẽ bị trình duyệt bỏ: người dùng "đăng nhập
     * thành công" nhưng mọi request sau đều 401. Ghi cảnh báo một lần để dễ chẩn đoán cấu hình.
     */
    private void warnIfSecureCookieOverHttp(HttpServletRequest request) {
        if (!properties.isSecure() || request.isSecure()) {
            return;
        }
        String host = request.getServerName() == null ? "" : request.getServerName().toLowerCase(Locale.ROOT);
        if (LOCAL_HOSTS.contains(host)) {
            return;
        }
        if (insecureWarningLogged.compareAndSet(false, true)) {
            log.warn("Cookie đăng nhập có thuộc tính Secure nhưng request tới qua HTTP (host={}): trình duyệt sẽ bỏ "
                    + "cookie và không giữ được phiên. Dùng HTTPS (khuyến nghị) hoặc đặt AUTH_COOKIE_SECURE=false.",
                    host);
        }
    }
}
