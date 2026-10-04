package com.education.base.security;

import com.education.base.service.AccessControlService;
import com.education.base.service.RefreshTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Đọc access token từ cookie HttpOnly {@value AuthCookieService#ACCESS_COOKIE} (đặt khi đăng nhập / làm mới),
 * kiểm tra chữ ký / hạn dùng, nạp lại người dùng
 * từ DB (phải còn {@code ACTIVE}, {@code TOKEN_VERSION} khớp, phiên {@code sid} chưa bị thu hồi) rồi gắn
 * {@link AuthUserPrincipal} vào {@code SecurityContext}. Claim đã xác thực được lưu ở request attribute
 * {@link #CLAIMS_ATTRIBUTE} (ví dụ để đăng xuất đúng phiên).
 * <p>
 * Token lỗi không chặn ngay: request tiếp tục ở trạng thái ẩn danh, endpoint công khai vẫn chạy bình thường,
 * endpoint cần đăng nhập nhận 401 kèm mã lỗi chi tiết từ {@link RestAuthenticationEntryPoint}.
 * <p>
 * Header {@code Authorization: Bearer} KHÔNG còn được chấp nhận: token không còn trả về cho JavaScript nên không
 * client hợp lệ nào cần tới; Swagger UI cũng dùng cookie (cùng origin). Chỉ một nguồn token giúp quy tắc CSRF
 * (mọi request ghi đều cần CSRF token) không có ngoại lệ.
 * <p>
 * Không khai báo là bean để không tự đăng ký vào servlet filter chain; {@code SecurityConfig} tạo trực tiếp.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR";
    public static final String CLAIMS_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".CLAIMS";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";

    private final JwtTokenService jwtTokenService;
    private final AccessControlService accessControlService;
    /** Có thể {@code null} (slice test): khi đó không kiểm tra phiên {@code sid}. */
    private final RefreshTokenService refreshTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, AccessControlService accessControlService) {
        this(jwtTokenService, accessControlService, null);
    }

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, AccessControlService accessControlService,
                                   RefreshTokenService refreshTokenService) {
        this.jwtTokenService = jwtTokenService;
        this.accessControlService = accessControlService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> cookieToken = AuthCookieService.readAccessToken(request);
        if (cookieToken.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = cookieToken.get();
            try {
                authenticate(token, request);
            } catch (InvalidTokenException ex) {
                log.debug("Access token bị từ chối: {} - {}", ex.getErrorCode(), ex.getMessage());
                SecurityContextHolder.clearContext();
                request.setAttribute(ERROR_ATTRIBUTE, ex);
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        if (token.isEmpty()) {
            throw new InvalidTokenException(InvalidTokenException.TOKEN_INVALID, "Access token không hợp lệ.");
        }
        JwtClaims claims = jwtTokenService.parse(token, TokenType.ACCESS);
        Optional<AuthUserPrincipal> loaded = accessControlService.loadActivePrincipal(claims.userId());
        if (loaded.isEmpty() || claims.tokenVersion() == null
                || loaded.get().getTokenVersion() != claims.tokenVersion()) {
            throw revoked();
        }
        if (refreshTokenService != null && claims.sessionId() != null
                && !refreshTokenService.isSessionActive(claims.sessionId())) {
            // Phiên đã đăng xuất / bị thu hồi do phát hiện dùng lại refresh token.
            throw revoked();
        }
        AuthUserPrincipal principal = loaded.get();
        if (claims.userType() != null && !claims.userType().equals(principal.getUserType().name())) {
            // Token phát hành cho loại tài khoản khác với hiện tại trong DB: không dùng lại.
            throw revoked();
        }
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.setAttribute(CLAIMS_ATTRIBUTE, claims);
    }

    private static InvalidTokenException revoked() {
        return new InvalidTokenException(TOKEN_REVOKED, "Phiên đăng nhập đã hết hiệu lực, vui lòng đăng nhập lại.");
    }
}
