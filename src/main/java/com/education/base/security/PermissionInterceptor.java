package com.education.base.security;

import com.education.base.exception.ForbiddenException;
import com.education.base.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Kiểm tra quyền trước khi vào Controller - TỪ CHỐI MẶC ĐỊNH:
 * <ul>
 *     <li>{@link PublicEndpoint}: bỏ qua mọi kiểm tra (đường dẫn phải {@code permitAll} trong {@code SecurityConfig}).</li>
 *     <li>Handler không khai báo {@link RequirePermission}, {@link AuthenticatedOnly} hay {@link PublicEndpoint}
 *     (trên method hoặc class) luôn bị từ chối 403, kể cả với quản trị viên.</li>
 *     <li>Người dùng bị buộc đổi mật khẩu chỉ được gọi endpoint có {@link AllowPendingPasswordChange}.</li>
 *     <li>{@link AuthenticatedOnly}: chỉ cần đăng nhập.</li>
 *     <li>{@link RequirePermission}: {@link Permissions#ADMIN_ROLE} luôn được phép; người dùng khác cần có ít nhất
 *     một mã quyền khai báo trong annotation.</li>
 * </ul>
 * Annotation trên method được ưu tiên hơn annotation trên class. Lỗi được {@code GlobalExceptionHandler}
 * bọc thành {@code ApiResponse} (401/403).
 */
@Slf4j
public class PermissionInterceptor implements HandlerInterceptor {

    public static final String FORBIDDEN_CODE = "FORBIDDEN";
    public static final String PASSWORD_CHANGE_REQUIRED_CODE = "PASSWORD_CHANGE_REQUIRED";

    /** Cách một handler được bảo vệ. */
    public enum AccessType {
        PUBLIC, AUTHENTICATED, PERMISSION, UNDECLARED
    }

    /** Kết quả phân giải annotation của một handler; {@code permission} chỉ có khi {@code type = PERMISSION}. */
    public record AccessRule(AccessType type, RequirePermission permission) {

        private static final AccessRule PUBLIC = new AccessRule(AccessType.PUBLIC, null);
        private static final AccessRule AUTHENTICATED = new AccessRule(AccessType.AUTHENTICATED, null);
        private static final AccessRule UNDECLARED = new AccessRule(AccessType.UNDECLARED, null);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        AccessRule rule = accessRule(handlerMethod.getMethod(), handlerMethod.getBeanType());
        if (rule.type() == AccessType.PUBLIC) {
            return true;
        }
        if (rule.type() == AccessType.UNDECLARED) {
            log.warn("Từ chối handler chưa khai báo quyền truy cập: {}#{}",
                    handlerMethod.getBeanType().getName(), handlerMethod.getMethod().getName());
            throw new ForbiddenException(FORBIDDEN_CODE,
                    "Chức năng chưa được cấu hình phân quyền, vui lòng liên hệ quản trị viên.");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (!authenticated) {
            throw new UnauthorizedException("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục.");
        }

        if (authentication.getPrincipal() instanceof AuthUserPrincipal principal
                && principal.isMustChangePassword()
                && !isAnnotated(handlerMethod, AllowPendingPasswordChange.class)) {
            throw new ForbiddenException(PASSWORD_CHANGE_REQUIRED_CODE,
                    "Bạn cần đổi mật khẩu trước khi tiếp tục sử dụng hệ thống.");
        }

        if (rule.type() == AccessType.AUTHENTICATED) {
            return true;
        }

        Set<String> granted = new HashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            granted.add(authority.getAuthority());
        }
        if (granted.contains(Permissions.ADMIN_ROLE)) {
            return true;
        }
        for (String permission : rule.permission().value()) {
            if (granted.contains(permission)) {
                return true;
            }
        }
        throw new ForbiddenException(FORBIDDEN_CODE, "Bạn không có quyền thực hiện chức năng này.");
    }

    /**
     * Phân giải cách bảo vệ handler: annotation trên method trước, sau đó trên class. Trên cùng một phần tử,
     * {@link RequirePermission} được ưu tiên hơn {@link AuthenticatedOnly}, rồi mới tới {@link PublicEndpoint}.
     */
    public static AccessRule accessRule(Method method, Class<?> beanType) {
        AccessRule onMethod = accessRuleOn(method);
        if (onMethod != null) {
            return onMethod;
        }
        AccessRule onType = accessRuleOn(beanType);
        return onType != null ? onType : AccessRule.UNDECLARED;
    }

    private static AccessRule accessRuleOn(AnnotatedElement element) {
        RequirePermission required = AnnotatedElementUtils.findMergedAnnotation(element, RequirePermission.class);
        if (required != null) {
            return new AccessRule(AccessType.PERMISSION, required);
        }
        if (AnnotatedElementUtils.hasAnnotation(element, AuthenticatedOnly.class)) {
            return AccessRule.AUTHENTICATED;
        }
        if (AnnotatedElementUtils.hasAnnotation(element, PublicEndpoint.class)) {
            return AccessRule.PUBLIC;
        }
        return null;
    }

    private static boolean isAnnotated(HandlerMethod handlerMethod,
                                       Class<? extends java.lang.annotation.Annotation> type) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), type)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), type);
    }
}
