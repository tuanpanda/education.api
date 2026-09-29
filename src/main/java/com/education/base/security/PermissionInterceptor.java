package com.education.base.security;

import com.education.base.exception.ForbiddenException;
import com.education.base.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashSet;
import java.util.Set;

/**
 * Kiểm tra quyền theo {@link RequirePermission} trước khi vào Controller.
 * <ul>
 *     <li>Người dùng bị buộc đổi mật khẩu chỉ được gọi endpoint có {@link AllowPendingPasswordChange}.</li>
 *     <li>{@link Permissions#ADMIN_ROLE} luôn được phép.</li>
 *     <li>Người dùng khác cần có ít nhất một mã quyền khai báo trong annotation.</li>
 * </ul>
 * Lỗi được {@code GlobalExceptionHandler} bọc thành {@code ApiResponse} (401/403).
 */
public class PermissionInterceptor implements HandlerInterceptor {

    public static final String FORBIDDEN_CODE = "FORBIDDEN";
    public static final String PASSWORD_CHANGE_REQUIRED_CODE = "PASSWORD_CHANGE_REQUIRED";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (authenticated
                && authentication.getPrincipal() instanceof AuthUserPrincipal principal
                && principal.isMustChangePassword()
                && !isAnnotated(handlerMethod, AllowPendingPasswordChange.class)) {
            throw new ForbiddenException(PASSWORD_CHANGE_REQUIRED_CODE,
                    "Bạn cần đổi mật khẩu trước khi tiếp tục sử dụng hệ thống.");
        }

        RequirePermission required = resolve(handlerMethod);
        if (required == null) {
            return true;
        }
        if (!authenticated) {
            throw new UnauthorizedException("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục.");
        }

        Set<String> granted = new HashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            granted.add(authority.getAuthority());
        }
        if (granted.contains(Permissions.ADMIN_ROLE)) {
            return true;
        }
        for (String permission : required.value()) {
            if (granted.contains(permission)) {
                return true;
            }
        }
        throw new ForbiddenException(FORBIDDEN_CODE, "Bạn không có quyền thực hiện chức năng này.");
    }

    private static RequirePermission resolve(HandlerMethod handlerMethod) {
        RequirePermission onMethod = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequirePermission.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequirePermission.class);
    }

    private static boolean isAnnotated(HandlerMethod handlerMethod,
                                       Class<? extends java.lang.annotation.Annotation> type) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), type)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), type);
    }
}
