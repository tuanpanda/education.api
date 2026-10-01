package com.education.base.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

/**
 * Trả 403 dạng {@code ApiResponse} khi Spring Security từ chối truy cập. Thiếu / sai CSRF token trả mã riêng
 * {@link #CSRF_TOKEN_INVALID} để frontend lấy token mới rồi gửi lại.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    public static final String CSRF_TOKEN_INVALID = "CSRF_TOKEN_INVALID";

    private final SecurityErrorWriter writer;

    public RestAccessDeniedHandler(SecurityErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            writer.write(response, HttpServletResponse.SC_FORBIDDEN, CSRF_TOKEN_INVALID,
                    "Phiên làm việc không hợp lệ hoặc đã hết hạn (CSRF). Vui lòng tải lại trang rồi thử lại.");
            return;
        }
        writer.write(response, HttpServletResponse.SC_FORBIDDEN, PermissionInterceptor.FORBIDDEN_CODE,
                "Bạn không có quyền thực hiện chức năng này.");
    }
}
