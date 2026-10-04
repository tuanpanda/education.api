package com.education.base.controller;

import com.education.base.exception.ForbiddenException;
import com.education.base.exception.UnauthorizedException;
import com.education.base.security.AllowPendingPasswordChange;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.PermissionInterceptor;
import com.education.base.security.PermissionInterceptor.AccessRule;
import com.education.base.security.PermissionInterceptor.AccessType;
import com.education.base.security.PermissionInterceptor.ApiZone;
import com.education.base.security.Permissions;
import com.education.base.security.UserType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hợp đồng phân quyền cổng học sinh (Phase 0), kiểm trên TOÀN BỘ endpoint: mọi handler của mọi
 * {@code @RestController} được phân giải ra mẫu đường dẫn thật bằng chính {@link RequestMappingHandlerMapping}
 * của Spring, rồi chạy {@link PermissionInterceptor#authorize} với từng loại tài khoản.
 *
 * <ul>
 *   <li>Ngoài {@code /api/v1/portal/**} và {@code /api/v1/auth/**}: tài khoản không phải STAFF luôn 403.</li>
 *   <li>{@code /api/v1/portal/**}: chỉ STUDENT.</li>
 *   <li>Học sinh đang {@code mustChangePassword}: chỉ gọi được {@code /api/v1/auth/**}.</li>
 *   <li>Hành vi của nhân viên KHÔNG đổi so với interceptor cũ (8491ee0) ở mọi endpoint ngoài cổng.</li>
 * </ul>
 * Ngoại lệ có chủ đích: endpoint {@code @PublicEndpoint} (login, csrf, health, ...) vẫn công khai với mọi người.
 */
class PortalAccessRulesTest {

    private static final String CONTROLLER_PACKAGE = "com.education.base.controller";

    /** Một endpoint: handler + một mẫu đường dẫn đã ghép prefix của class. */
    record Endpoint(Class<?> controller, Method method, String pattern, String httpMethods) {
        String id() {
            return httpMethods + " " + pattern + " (" + controller.getSimpleName() + "#" + method.getName() + ")";
        }

        AccessRule rule() {
            return PermissionInterceptor.accessRule(method, controller);
        }

        ApiZone zone() {
            return PermissionInterceptor.zoneOf(pattern);
        }
    }

    /** Mở {@code getMappingForMethod} để lấy mẫu đường dẫn đúng như Spring đăng ký lúc chạy. */
    static final class MappingResolver extends RequestMappingHandlerMapping {
        RequestMappingInfo resolve(Method method, Class<?> type) {
            return getMappingForMethod(method, type);
        }
    }

    private static List<Endpoint> endpoints;

    @BeforeAll
    static void scan() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        MappingResolver resolver = new MappingResolver();
        endpoints = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller = ClassUtils.forName(definition.getBeanClassName(),
                    PortalAccessRulesTest.class.getClassLoader());
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isSynthetic() || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                RequestMappingInfo info = resolver.resolve(method, controller);
                if (info == null) {
                    continue;
                }
                String httpMethods = info.getMethodsCondition().getMethods().isEmpty()
                        ? "ANY" : info.getMethodsCondition().getMethods().toString();
                for (String pattern : info.getPatternValues()) {
                    endpoints.add(new Endpoint(controller, method, pattern, httpMethods));
                }
            }
        }
    }

    // ------------------------------------------------------------------ principals

    private static final Set<String> ALL_PERMISSIONS = allDeclaredPermissions();

    private static Authentication auth(AuthUserPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static AuthUserPrincipal staffAdmin(boolean mustChange) {
        return AuthUserPrincipal.builder().id(1L).username("admin").roles(List.of(Permissions.ADMIN_ROLE))
                .permissions(Set.of()).mustChangePassword(mustChange).userType(UserType.STAFF).build();
    }

    private static AuthUserPrincipal staffTeacher(boolean mustChange) {
        return AuthUserPrincipal.builder().id(2L).username("teacher").roles(List.of("ROLE_TEACHER"))
                .permissions(Set.of("MENU_CLASS_LIST:VIEW", "MENU_ATTENDANCE:VIEW"))
                .mustChangePassword(mustChange).userType(UserType.STAFF).build();
    }

    /**
     * Học sinh "tệ nhất": lỡ bị gán ROLE_ADMIN và mọi quyền - chốt chặn vẫn phải dựa vào USER_TYPE chứ không vào quyền.
     */
    private static AuthUserPrincipal student(boolean mustChange) {
        return AuthUserPrincipal.builder().id(9L).username("hs00001")
                .roles(List.of(Permissions.STUDENT_ROLE, Permissions.ADMIN_ROLE))
                .permissions(ALL_PERMISSIONS).mustChangePassword(mustChange)
                .userType(UserType.STUDENT).studentId(42L).build();
    }

    private static AuthUserPrincipal parent() {
        return AuthUserPrincipal.builder().id(10L).username("ph00001").roles(List.of(Permissions.ADMIN_ROLE))
                .permissions(ALL_PERMISSIONS).userType(UserType.PARENT).build();
    }

    /** Kết quả: "OK", "401" hoặc "403:&lt;mã lỗi&gt;". */
    private static String decide(Endpoint endpoint, Authentication authentication) {
        try {
            PermissionInterceptor.authorize(endpoint.method(), endpoint.controller(), endpoint.pattern(),
                    authentication);
            return "OK";
        } catch (UnauthorizedException ex) {
            return "401";
        } catch (ForbiddenException ex) {
            return "403:" + ex.getErrorCode();
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void scanFindsAllEndpointsIncludingPortalAndStudentAccounts() {
        assertThat(endpoints).as("số endpoint được quét").hasSizeGreaterThan(60);
        assertThat(endpoints).extracting(Endpoint::pattern)
                .contains("/api/v1/portal/me",
                        "/api/v1/auth/me",
                        "/api/v1/student-accounts/search",
                        "/api/v1/student-accounts/bulk",
                        "/api/v1/student-accounts/{userId}/reset-password",
                        "/api/v1/student-accounts/{userId}/lock",
                        "/api/v1/student-accounts/{userId}/unlock");
        assertThat(endpoints).allSatisfy(endpoint ->
                assertThat(endpoint.rule().type()).as(endpoint.id()).isNotEqualTo(AccessType.UNDECLARED));
    }

    @Test
    void portalHandlersAndPortalAccessAnnotationMatchExactly() {
        Set<String> mismatched = new TreeSet<>();
        for (Endpoint endpoint : endpoints) {
            boolean inPortal = endpoint.zone() == ApiZone.PORTAL;
            boolean portalRule = endpoint.rule().type() == AccessType.PORTAL;
            if (inPortal != portalRule) {
                mismatched.add(endpoint.id() + " zone=" + endpoint.zone() + " rule=" + endpoint.rule().type());
            }
        }
        assertThat(mismatched)
                .as("Handler /api/v1/portal/** phải có @PortalAccess và @PortalAccess chỉ dùng trong /api/v1/portal/**")
                .isEmpty();
    }

    @Test
    void nonStaffAccountsAreForbiddenOutsidePortalAndAuth() {
        Map<String, Authentication> nonStaff = new LinkedHashMap<>();
        nonStaff.put("STUDENT", auth(student(false)));
        nonStaff.put("STUDENT(mustChange)", auth(student(true)));
        nonStaff.put("PARENT", auth(parent()));

        Set<String> violations = new TreeSet<>();
        int checked = 0;
        for (Endpoint endpoint : endpoints) {
            if (endpoint.zone() != ApiZone.STAFF || endpoint.rule().type() == AccessType.PUBLIC) {
                continue;
            }
            for (Map.Entry<String, Authentication> entry : nonStaff.entrySet()) {
                checked++;
                String result = decide(endpoint, entry.getValue());
                if (!result.equals("403:" + PermissionInterceptor.STAFF_ONLY_CODE)) {
                    violations.add(entry.getKey() + " -> " + endpoint.id() + " = " + result);
                }
            }
        }
        assertThat(checked).isGreaterThan(150);
        assertThat(violations).as("Tài khoản không phải STAFF lọt vào API nội bộ").isEmpty();
    }

    @Test
    void portalIsStudentOnly() {
        List<Endpoint> portal = endpoints.stream().filter(e -> e.zone() == ApiZone.PORTAL).toList();
        assertThat(portal).isNotEmpty();
        for (Endpoint endpoint : portal) {
            assertThat(decide(endpoint, auth(student(false)))).as("STUDENT " + endpoint.id()).isEqualTo("OK");
            assertThat(decide(endpoint, auth(staffAdmin(false)))).as("STAFF admin " + endpoint.id())
                    .isEqualTo("403:" + PermissionInterceptor.STUDENT_ONLY_CODE);
            assertThat(decide(endpoint, auth(staffTeacher(false)))).as("STAFF teacher " + endpoint.id())
                    .isEqualTo("403:" + PermissionInterceptor.STUDENT_ONLY_CODE);
            assertThat(decide(endpoint, auth(parent()))).as("PARENT " + endpoint.id())
                    .isEqualTo("403:" + PermissionInterceptor.STUDENT_ONLY_CODE);
            assertThat(decide(endpoint, null)).as("anonymous " + endpoint.id()).isEqualTo("401");
        }
    }

    @Test
    void studentWithPendingPasswordChangeCanOnlyUseAuthApi() {
        Authentication pending = auth(student(true));
        Set<String> violations = new TreeSet<>();
        for (Endpoint endpoint : endpoints) {
            if (endpoint.rule().type() == AccessType.PUBLIC) {
                continue;
            }
            String result = decide(endpoint, pending);
            boolean ok = result.equals("OK");
            if (endpoint.zone() == ApiZone.AUTH) {
                if (!ok) {
                    violations.add("phải cho phép: " + endpoint.id() + " = " + result);
                }
            } else if (ok || !result.startsWith("403:")) {
                violations.add("phải chặn: " + endpoint.id() + " = " + result);
            }
        }
        assertThat(violations).isEmpty();
        Endpoint portalMe = endpoint("/api/v1/portal/me");
        assertThat(decide(portalMe, pending)).isEqualTo("403:" + PermissionInterceptor.PASSWORD_CHANGE_REQUIRED_CODE);
    }

    @Test
    void authApiStaysAvailableToEveryAuthenticatedAccountType() {
        for (Endpoint endpoint : endpoints) {
            if (endpoint.zone() != ApiZone.AUTH || endpoint.rule().type() == AccessType.PUBLIC) {
                continue;
            }
            assertThat(endpoint.rule().type()).as(endpoint.id()).isEqualTo(AccessType.AUTHENTICATED);
            assertThat(decide(endpoint, auth(student(false)))).as(endpoint.id()).isEqualTo("OK");
            assertThat(decide(endpoint, auth(parent()))).as(endpoint.id()).isEqualTo("OK");
        }
    }

    @Test
    void publicEndpointsStayPublicForEveryone() {
        List<Endpoint> publicEndpoints = endpoints.stream()
                .filter(e -> e.rule().type() == AccessType.PUBLIC).toList();
        assertThat(publicEndpoints).isNotEmpty();
        for (Endpoint endpoint : publicEndpoints) {
            assertThat(endpoint.zone()).as(endpoint.id()).isNotEqualTo(ApiZone.PORTAL);
            for (Authentication authentication : List.of(auth(student(true)), auth(parent()), auth(staffAdmin(true)))) {
                assertThat(decide(endpoint, authentication)).as(endpoint.id()).isEqualTo("OK");
            }
        }
    }

    @Test
    void staffBehaviourIsUnchangedOutsidePortal() {
        Map<String, Authentication> staff = new LinkedHashMap<>();
        staff.put("admin", auth(staffAdmin(false)));
        staff.put("admin(mustChange)", auth(staffAdmin(true)));
        staff.put("teacher", auth(staffTeacher(false)));
        staff.put("teacher(mustChange)", auth(staffTeacher(true)));
        staff.put("anonymous", null);

        Set<String> differences = new TreeSet<>();
        for (Endpoint endpoint : endpoints) {
            if (endpoint.zone() == ApiZone.PORTAL) {
                continue;
            }
            for (Map.Entry<String, Authentication> entry : staff.entrySet()) {
                String now = decide(endpoint, entry.getValue());
                String before = legacyDecision(endpoint, entry.getValue());
                if (!now.equals(before)) {
                    differences.add(entry.getKey() + " -> " + endpoint.id() + ": trước=" + before + ", nay=" + now);
                }
            }
        }
        assertThat(differences).as("Phân quyền nhân viên phải giữ nguyên như trước Phase 0").isEmpty();
    }

    @Test
    void portalHandlersNeverTakeStudentIdFromTheClient() {
        Set<String> violations = new TreeSet<>();
        for (Endpoint endpoint : endpoints) {
            if (endpoint.zone() != ApiZone.PORTAL) {
                continue;
            }
            for (int i = 0; i < endpoint.method().getParameterCount(); i++) {
                MethodParameter parameter = new MethodParameter(endpoint.method(), i);
                Set<String> names = new HashSet<>();
                if (endpoint.method().getParameters()[i].isNamePresent()) {
                    names.add(endpoint.method().getParameters()[i].getName());
                }
                RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
                if (requestParam != null) {
                    names.add(requestParam.value());
                    names.add(requestParam.name());
                }
                PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
                if (pathVariable != null) {
                    names.add(pathVariable.value());
                    names.add(pathVariable.name());
                }
                Class<?> type = parameter.getParameterType();
                if (!type.isPrimitive() && type.getName().startsWith("com.education")) {
                    for (Field field : type.getDeclaredFields()) {
                        names.add(field.getName());
                    }
                }
                if (names.stream().anyMatch(name -> name.equalsIgnoreCase("studentId"))) {
                    violations.add(endpoint.id() + " tham số #" + i);
                }
            }
        }
        assertThat(violations).as("Cổng học sinh phải lấy học sinh từ người đăng nhập, không nhận studentId từ client")
                .isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private static Endpoint endpoint(String pattern) {
        return endpoints.stream().filter(e -> e.pattern().equals(pattern)).findFirst().orElseThrow();
    }

    /** Bản sao trung thành thuật toán của interceptor trước Phase 0 (commit 8491ee0). */
    private static String legacyDecision(Endpoint endpoint, Authentication authentication) {
        AccessRule rule = endpoint.rule();
        if (rule.type() == AccessType.PUBLIC) {
            return "OK";
        }
        if (rule.type() == AccessType.UNDECLARED) {
            return "403:" + PermissionInterceptor.FORBIDDEN_CODE;
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return "401";
        }
        if (authentication.getPrincipal() instanceof AuthUserPrincipal principal
                && principal.isMustChangePassword()
                && !(AnnotatedElementUtils.hasAnnotation(endpoint.method(), AllowPendingPasswordChange.class)
                || AnnotatedElementUtils.hasAnnotation(endpoint.controller(), AllowPendingPasswordChange.class))) {
            return "403:" + PermissionInterceptor.PASSWORD_CHANGE_REQUIRED_CODE;
        }
        if (rule.type() == AccessType.AUTHENTICATED) {
            return "OK";
        }
        Set<String> granted = new HashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            granted.add(authority.getAuthority());
        }
        if (granted.contains(Permissions.ADMIN_ROLE)) {
            return "OK";
        }
        for (String permission : rule.permission().value()) {
            if (granted.contains(permission)) {
                return "OK";
            }
        }
        return "403:" + PermissionInterceptor.FORBIDDEN_CODE;
    }

    /** Mọi hằng quyền {@code MENU_*:*} khai báo trong {@link Permissions}. */
    private static Set<String> allDeclaredPermissions() {
        Set<String> result = new HashSet<>();
        for (Field field : Permissions.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    String value = (String) field.get(null);
                    if (value != null && value.contains(":")) {
                        result.add(value);
                    }
                } catch (IllegalAccessException ignored) {
                    // hằng private: bỏ qua
                }
            }
        }
        return Set.copyOf(result);
    }
}
