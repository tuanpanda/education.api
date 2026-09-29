package com.education.base.security;

import com.education.base.service.AccessControlService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Đọc access token ({@code Authorization: Bearer ...}), kiểm tra chữ ký / hạn dùng, nạp lại người dùng
 * từ DB (phải còn {@code ACTIVE}, {@code TOKEN_VERSION} khớp) rồi gắn {@link AuthUserPrincipal} vào
 * {@code SecurityContext}.
 * <p>
 * Token lỗi không chặn ngay: request tiếp tục ở trạng thái ẩn danh, endpoint công khai vẫn chạy bình thường,
 * endpoint cần đăng nhập nhận 401 kèm mã lỗi chi tiết từ {@link RestAuthenticationEntryPoint}.
 * <p>
 * Không khai báo là bean để không tự đăng ký vào servlet filter chain; {@code SecurityConfig} tạo trực tiếp.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final AccessControlService accessControlService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, AccessControlService accessControlService) {
        this.jwtTokenService = jwtTokenService;
        this.accessControlService = accessControlService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
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
            throw new InvalidTokenException(TOKEN_REVOKED,
                    "Phiên đăng nhập đã hết hiệu lực, vui lòng đăng nhập lại.");
        }
        AuthUserPrincipal principal = loaded.get();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
