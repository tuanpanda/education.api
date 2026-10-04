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
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Kiểm tra quyền trước khi vào Controller - TỪ CHỐI MẶC ĐỊNH:
 * <ul>
 *     <li>{@link PublicEndpoint}: bỏ qua mọi kiểm tra (đường dẫn phải {@code permitAll} trong {@code SecurityConfig}).</li>
 *     <li>Handler không khai báo {@link RequirePermission}, {@link AuthenticatedOnly}, {@link PortalAccess} hay
 *     {@link PublicEndpoint} (trên method hoặc class) luôn bị từ chối 403, kể cả với quản trị viên.</li>
 *     <li>Hàng rào theo loại tài khoản ({@link UserType}, V17) - xét theo vùng API ({@link ApiZone}) của handler,
 *     TRƯỚC mọi kiểm tra quyền và trước ngoại lệ {@code ROLE_ADMIN}:
 *         <ul>
 *             <li>{@link ApiZone#AUTH} ({@code /api/v1/auth/**}): mọi loại tài khoản;</li>
 *             <li>{@link ApiZone#PORTAL} ({@code /api/v1/portal/**}): chỉ loại tài khoản khai báo trong
 *             {@link PortalAccess} (giai đoạn 0: {@code STUDENT}); nhân viên nhận 403 {@value #STUDENT_ONLY_CODE};</li>
 *             <li>{@link ApiZone#STAFF} (mọi API khác): chỉ {@code STAFF}; tài khoản khác nhận 403
 *             {@value #STAFF_ONLY_CODE}.</li>
 *         </ul>
 *     </li>
 *     <li>Người dùng bị buộc đổi mật khẩu: nhân viên chỉ được gọi endpoint có {@link AllowPendingPasswordChange};
 *     học sinh / phụ huynh chỉ được gọi {@code /api/v1/auth/**}.</li>
 *     <li>{@link AuthenticatedOnly}: chỉ cần đăng nhập (và qua hàng rào theo loại tài khoản).</li>
 *     <li>{@link RequirePermission}: chỉ nhân viên; {@link Permissions#ADMIN_ROLE} luôn được phép; người dùng khác
 *     cần có ít nhất một mã quyền khai báo trong annotation.</li>
 * </ul>
 * Annotation trên method được ưu tiên hơn annotation trên class. Lỗi được {@code GlobalExceptionHandler}
 * bọc thành {@code ApiResponse} (401/403).
 * <p>
 * Vùng API lấy từ mẫu đường dẫn của chính handler ({@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}), không
 * từ URL thô, nên không thể "mượn" tiền tố {@code /api/v1/auth} để vào handler quản trị.
 */
@Slf4j
public class PermissionInterceptor implements HandlerInterceptor {

    public static final String FORBIDDEN_CODE = "FORBIDDEN";
    public static final String PASSWORD_CHANGE_REQUIRED_CODE = "PASSWORD_CHANGE_REQUIRED";
    /** Tài khoản không phải nhân viên gọi API quản trị. */
    public static final String STAFF_ONLY_CODE = "STAFF_ONLY";
    /** Tài khoản không được phép (ví dụ nhân viên) gọi API cổng học sinh. */
    public static final String STUDENT_ONLY_CODE = "STUDENT_ONLY";

    public static final String AUTH_PATH_PREFIX = "/api/v1/auth";
    public static final String PORTAL_PATH_PREFIX = "/api/v1/portal";

    /** Cách một handler được bảo vệ. */
    public enum AccessType {
        PUBLIC, AUTHENTICATED, PERMISSION, PORTAL, UNDECLARED
    }

    /** Vùng API theo đường dẫn handler. */
    public enum ApiZone {
        /** {@code /api/v1/auth/**}: đăng nhập, đổi mật khẩu, thông tin phiên - mọi loại tài khoản. */
        AUTH,
        /** {@code /api/v1/portal/**}: cổng học sinh. */
        PORTAL,
        /** Mọi API khác: chỉ nhân viên. */
        STAFF
    }

    /**
     * Kết quả phân giải annotation của một handler; {@code permission} chỉ có khi {@code type = PERMISSION},
     * {@code portal} chỉ có khi {@code type = PORTAL}.
     */
    public record AccessRule(AccessType type, RequirePermission permission, PortalAccess portal) {

        private static final AccessRule PUBLIC = new AccessRule(AccessType.PUBLIC, null, null);
        private static final AccessRule AUTHENTICATED = new AccessRule(AccessType.AUTHENTICATED, null, null);
        private static final AccessRule UNDECLARED = new AccessRule(AccessType.UNDECLARED, null, null);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        authorize(handlerMethod.getMethod(), handlerMethod.getBeanType(), handlerPath(request),
                SecurityContextHolder.getContext().getAuthentication());
        return true;
    }

    /**
     * Toàn bộ quyết định phân quyền cho handler {@code method} của {@code beanType}, mẫu đường dẫn {@code path}
     * và người dùng {@code authentication}. Ném {@link UnauthorizedException} (401) / {@link ForbiddenException}
     * (403) khi bị từ chối. Tách riêng để test duyệt được mọi endpoint ({@code PortalAccessRulesTest}).
     */
    public static void authorize(Method method, Class<?> beanType, String path, Authentication authentication) {
        AccessRule rule = accessRule(method, beanType);
        if (rule.type() == AccessType.PUBLIC) {
            return;
        }
        if (rule.type() == AccessType.UNDECLARED) {
            log.warn("Từ chối handler chưa khai báo quyền truy cập: {}#{}", beanType.getName(), method.getName());
            throw new ForbiddenException(FORBIDDEN_CODE,
                    "Chức năng chưa được cấu hình phân quyền, vui lòng liên hệ quản trị viên.");
        }

        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (!authenticated) {
            throw new UnauthorizedException("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục.");
        }

        AuthUserPrincipal principal = authentication.getPrincipal() instanceof AuthUserPrincipal p ? p : null;
        // Principal lạ (không do JwtAuthenticationFilter dựng) -> không coi là nhân viên (từ chối an toàn).
        UserType userType = principal == null ? null : principal.getUserType();
        boolean staff = userType == UserType.STAFF;
        ApiZone zone = zoneOf(path);

        if (!staff && (zone == ApiZone.STAFF || rule.type() == AccessType.PERMISSION)) {
            throw new ForbiddenException(STAFF_ONLY_CODE,
                    "Tài khoản của bạn không được sử dụng chức năng quản trị.");
        }

        boolean mustChangePassword = principal != null && principal.isMustChangePassword();
        if (mustChangePassword) {
            boolean allowed = staff
                    ? isAnnotated(method, beanType, AllowPendingPasswordChange.class)
                    : zone == ApiZone.AUTH;
            if (!allowed) {
                throw new ForbiddenException(PASSWORD_CHANGE_REQUIRED_CODE,
                        "Bạn cần đổi mật khẩu trước khi tiếp tục sử dụng hệ thống.");
            }
        }

        if (zone == ApiZone.PORTAL || rule.type() == AccessType.PORTAL) {
            if (zone != ApiZone.PORTAL || rule.type() != AccessType.PORTAL) {
                // @PortalAccess ngoài /api/v1/portal/** hoặc handler cổng thiếu @PortalAccess: cấu hình sai.
                log.warn("Từ chối handler cổng cấu hình sai (zone={}, rule={}): {}#{}",
                        zone, rule.type(), beanType.getName(), method.getName());
                throw new ForbiddenException(FORBIDDEN_CODE,
                        "Chức năng chưa được cấu hình phân quyền, vui lòng liên hệ quản trị viên.");
            }
            if (userType == null || !Arrays.asList(rule.portal().value()).contains(userType)) {
                throw new ForbiddenException(STUDENT_ONLY_CODE, "Chức năng này chỉ dành cho tài khoản học sinh.");
            }
            return;
        }

        if (rule.type() == AccessType.AUTHENTICATED) {
            return;
        }

        Set<String> granted = new HashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            granted.add(authority.getAuthority());
        }
        if (granted.contains(Permissions.ADMIN_ROLE)) {
            return;
        }
        for (String permission : rule.permission().value()) {
            if (granted.contains(permission)) {
                return;
            }
        }
        throw new ForbiddenException(FORBIDDEN_CODE, "Bạn không có quyền thực hiện chức năng này.");
    }

    /**
     * Vùng API của mẫu đường dẫn (hoặc đường dẫn) {@code path}; so khớp theo từng đoạn nên
     * {@code /api/v1/authx} hay {@code /api/v1/portalx} vẫn là {@link ApiZone#STAFF}.
     */
    public static ApiZone zoneOf(String path) {
        if (path == null) {
            return ApiZone.STAFF;
        }
        if (hasPrefix(path, AUTH_PATH_PREFIX)) {
            return ApiZone.AUTH;
        }
        if (hasPrefix(path, PORTAL_PATH_PREFIX)) {
            return ApiZone.PORTAL;
        }
        return ApiZone.STAFF;
    }

    /**
     * Phân giải cách bảo vệ handler: annotation trên method trước, sau đó trên class. Trên cùng một phần tử,
     * {@link RequirePermission} được ưu tiên hơn {@link PortalAccess}, rồi {@link AuthenticatedOnly}, rồi mới tới
     * {@link PublicEndpoint}.
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
            return new AccessRule(AccessType.PERMISSION, required, null);
        }
        PortalAccess portal = AnnotatedElementUtils.findMergedAnnotation(element, PortalAccess.class);
        if (portal != null) {
            return new AccessRule(AccessType.PORTAL, null, portal);
        }
        if (AnnotatedElementUtils.hasAnnotation(element, AuthenticatedOnly.class)) {
            return AccessRule.AUTHENTICATED;
        }
        if (AnnotatedElementUtils.hasAnnotation(element, PublicEndpoint.class)) {
            return AccessRule.PUBLIC;
        }
        return null;
    }

    /** Mẫu đường dẫn của handler đã khớp; dự phòng: đường dẫn trong ứng dụng. */
    private static String handlerPath(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String value && !value.isEmpty()) {
            return value;
        }
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    private static boolean hasPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean isAnnotated(Method method, Class<?> beanType,
                                       Class<? extends java.lang.annotation.Annotation> type) {
        return AnnotatedElementUtils.hasAnnotation(method, type)
                || AnnotatedElementUtils.hasAnnotation(beanType, type);
    }
}
