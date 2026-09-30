package com.education.base.security;

import com.education.base.exception.ForbiddenException;
import com.education.base.exception.UnauthorizedException;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionInterceptorTest {

    private final PermissionInterceptor interceptor = new PermissionInterceptor();

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    static class MethodLevel {
        public void undeclared() {
        }

        @AuthenticatedOnly
        public void authenticatedOnly() {
        }

        @PublicEndpoint
        public void open() {
        }

        @RequirePermission({"MENU_X:VIEW", "MENU_Y:VIEW"})
        public void guarded() {
        }

        @AuthenticatedOnly
        @AllowPendingPasswordChange
        public void allowedWhilePending() {
        }
    }

    @RequirePermission("MENU_X:VIEW")
    static class ClassLevel {
        public void inherited() {
        }

        @AuthenticatedOnly
        public void overridden() {
        }
    }

    @AuthenticatedOnly
    static class AuthenticatedClass {
        public void inherited() {
        }
    }

    private boolean call(Class<?> type, String method) throws Exception {
        Object bean = type.getDeclaredConstructor().newInstance();
        HandlerMethod handler = new HandlerMethod(bean, type.getMethod(method));
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);
    }

    private static void loginUser(String... permissions) {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"), Set.of(permissions));
    }

    private static void loginAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    @Test
    void nonHandlerMethod_isPassedThrough() throws Exception {
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    @Test
    void undeclaredHandler_isDeniedForUser() {
        loginUser("MENU_X:VIEW");

        assertThatThrownBy(() -> call(MethodLevel.class, "undeclared"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.FORBIDDEN_CODE);
    }

    @Test
    void undeclaredHandler_isDeniedEvenForAdmin() {
        TestSecurityContexts.loginAdmin(1L);

        assertThatThrownBy(() -> call(MethodLevel.class, "undeclared"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void authenticatedOnly_allowsAnyLoggedInUser() throws Exception {
        loginUser();

        assertThat(call(MethodLevel.class, "authenticatedOnly")).isTrue();
    }

    @Test
    void authenticatedOnly_rejectsAnonymous() {
        loginAnonymous();

        assertThatThrownBy(() -> call(MethodLevel.class, "authenticatedOnly"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void authenticatedOnly_rejectsMissingAuthentication() {
        assertThatThrownBy(() -> call(MethodLevel.class, "authenticatedOnly"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void authenticatedOnly_onClass_appliesToMethods() throws Exception {
        loginUser();

        assertThat(call(AuthenticatedClass.class, "inherited")).isTrue();
    }

    @Test
    void publicEndpoint_allowsAnonymous() throws Exception {
        loginAnonymous();

        assertThat(call(MethodLevel.class, "open")).isTrue();
    }

    @Test
    void publicEndpoint_allowsMissingAuthentication() throws Exception {
        assertThat(call(MethodLevel.class, "open")).isTrue();
    }

    @Test
    void requirePermission_anyOfMatches() throws Exception {
        loginUser("MENU_Y:VIEW");

        assertThat(call(MethodLevel.class, "guarded")).isTrue();
    }

    @Test
    void requirePermission_withoutPermission_isForbidden() {
        loginUser("MENU_Z:VIEW");

        assertThatThrownBy(() -> call(MethodLevel.class, "guarded"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.FORBIDDEN_CODE);
    }

    @Test
    void requirePermission_adminBypasses() throws Exception {
        TestSecurityContexts.loginAdmin(1L);

        assertThat(call(MethodLevel.class, "guarded")).isTrue();
    }

    @Test
    void requirePermission_anonymous_isUnauthorized() {
        loginAnonymous();

        assertThatThrownBy(() -> call(MethodLevel.class, "guarded"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void classLevelPermission_appliesToUnannotatedMethod() {
        loginUser();

        assertThatThrownBy(() -> call(ClassLevel.class, "inherited")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void methodLevelAnnotation_overridesClassLevel() throws Exception {
        loginUser();

        assertThat(call(ClassLevel.class, "overridden")).isTrue();
    }

    @Test
    void pendingPasswordChange_blocksAuthenticatedOnlyWithoutAllowAnnotation() {
        TestSecurityContexts.clear();
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(5L).username("u5").roles(List.of("ROLE_STAFF")).permissions(Set.of()).mustChangePassword(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));

        assertThatThrownBy(() -> call(MethodLevel.class, "authenticatedOnly"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.PASSWORD_CHANGE_REQUIRED_CODE);
    }

    @Test
    void pendingPasswordChange_allowsAnnotatedHandler() throws Exception {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(5L).username("u5").roles(List.of("ROLE_STAFF")).permissions(Set.of()).mustChangePassword(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));

        assertThat(call(MethodLevel.class, "allowedWhilePending")).isTrue();
    }

    @Test
    void accessRule_resolvesAllTypes() throws Exception {
        assertThat(PermissionInterceptor.accessRule(MethodLevel.class.getMethod("undeclared"), MethodLevel.class).type())
                .isEqualTo(PermissionInterceptor.AccessType.UNDECLARED);
        assertThat(PermissionInterceptor.accessRule(MethodLevel.class.getMethod("open"), MethodLevel.class).type())
                .isEqualTo(PermissionInterceptor.AccessType.PUBLIC);
        assertThat(PermissionInterceptor.accessRule(MethodLevel.class.getMethod("guarded"), MethodLevel.class)
                .permission().value()).containsExactly("MENU_X:VIEW", "MENU_Y:VIEW");
    }
}
