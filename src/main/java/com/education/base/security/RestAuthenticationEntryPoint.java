package com.education.base.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Trả 401 dạng {@code ApiResponse} khi gọi API cần đăng nhập mà thiếu / sai access token.
 * Mã lỗi chi tiết ({@code TOKEN_EXPIRED}, {@code TOKEN_INVALID}, {@code TOKEN_REVOKED}) do
 * {@link JwtAuthenticationFilter} đặt vào request attribute {@link JwtAuthenticationFilter#ERROR_ATTRIBUTE}.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter writer;

    public RestAuthenticationEntryPoint(SecurityErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object detail = request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE);
        if (detail instanceof InvalidTokenException invalid) {
            writer.write(response, HttpServletResponse.SC_UNAUTHORIZED, invalid.getErrorCode(), invalid.getMessage());
            return;
        }
        writer.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Vui lòng đăng nhập để tiếp tục.");
    }
}
