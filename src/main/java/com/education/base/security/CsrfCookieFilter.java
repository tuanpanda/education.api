package com.education.base.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6 nạp CSRF token "lười": nếu không ai đọc thì cookie {@code XSRF-TOKEN} không bao giờ được
 * đặt. Filter này đọc token ở mọi request để cookie luôn có sẵn (Swagger UI đọc cookie này; frontend lấy token
 * qua {@code GET /api/v1/auth/csrf}). Token chỉ được sinh mới khi request chưa có cookie.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object token = request.getAttribute(CsrfToken.class.getName());
        if (token instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
        chain.doFilter(request, response);
    }
}
