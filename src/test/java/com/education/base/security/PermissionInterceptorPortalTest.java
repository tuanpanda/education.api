package com.education.base.security;

import com.education.base.exception.ForbiddenException;
import com.education.base.security.PermissionInterceptor.ApiZone;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vùng API (auth / portal / staff) và chốt chặn theo loại tài khoản của {@link PermissionInterceptor}. */
class PermissionInterceptorPortalTest {

    private final PermissionInterceptor interceptor = new PermissionInterceptor();

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    static class Handlers {
        @PortalAccess
        public void portal() {
        }

        @PortalAccess({UserType.STUDENT, UserType.PARENT})
        public void portalFamily() {
        }

        @AuthenticatedOnly
        public void authenticated() {
        }

        @RequirePermission("MENU_X:VIEW")
        public void guarded() {
        }
    }

    private boolean call(String method, String pattern, String uri) throws Exception {
        HandlerMethod handler = new HandlerMethod(new Handlers(), Handlers.class.getMethod(method));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (pattern != null) {
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        }
        return interceptor.preHandle(request, new MockHttpServletResponse(), handler);
    }

    private static void loginParent() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder().id(3L).username("ph1")
                .roles(List.of()).permissions(Set.of()).userType(UserType.PARENT).build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }

    @Test
    void zoneOf_matchesWholeSegmentsOnly() {
        assertThat(PermissionInterceptor.zoneOf("/api/v1/auth")).isEqualTo(ApiZone.AUTH);
        assertThat(PermissionInterceptor.zoneOf("/api/v1/auth/me")).isEqualTo(ApiZone.AUTH);
        assertThat(PermissionInterceptor.zoneOf("/api/v1/portal/me")).isEqualTo(ApiZone.PORTAL);
        assertThat(PermissionInterceptor.zoneOf("/api/v1/portalx/me")).isEqualTo(ApiZone.STAFF);
        assertThat(PermissionInterceptor.zoneOf("/api/v1/authx")).isEqualTo(ApiZone.STAFF);
        assertThat(PermissionInterceptor.zoneOf("/api/v1/students")).isEqualTo(ApiZone.STAFF);
        assertThat(PermissionInterceptor.zoneOf("/API/V1/PORTAL/me")).isEqualTo(ApiZone.STAFF);
        assertThat(PermissionInterceptor.zoneOf(null)).isEqualTo(ApiZone.STAFF);
    }

    @Test
    void student_portalHandler_allowed_usingMatchedPattern() throws Exception {
        TestSecurityContexts.loginStudent(9L, 42L, false);

        assertThat(call("portal", "/api/v1/portal/me", "/api/v1/portal/me")).isTrue();
    }

    @Test
    void zone_fallsBackToRequestPath_whenNoPatternAttribute() throws Exception {
        TestSecurityContexts.loginStudent(9L, 42L, false);

        assertThat(call("portal", null, "/api/v1/portal/me")).isTrue();
        assertThatThrownBy(() -> call("authenticated", null, "/api/v1/classes"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.STAFF_ONLY_CODE);
    }

    @Test
    void student_staffZone_isStaffOnly_evenForAuthenticatedOnlyHandlers() {
        TestSecurityContexts.loginStudent(9L, 42L, false);

        assertThatThrownBy(() -> call("authenticated", "/api/v1/menus/user-navigation", "/api/v1/menus/user-navigation"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.STAFF_ONLY_CODE);
    }

    @Test
    void student_permissionHandler_isStaffOnly_evenInsideAuthZone() {
        TestSecurityContexts.loginStudent(9L, 42L, false);

        assertThatThrownBy(() -> call("guarded", "/api/v1/auth/something", "/api/v1/auth/something"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.STAFF_ONLY_CODE);
    }

    @Test
    void student_authZone_allowedEvenWithPendingPasswordChange() throws Exception {
        TestSecurityContexts.loginStudent(9L, 42L, true);

        assertThat(call("authenticated", "/api/v1/auth/me", "/api/v1/auth/me")).isTrue();
    }

    @Test
    void student_pendingPasswordChange_portalDenied() {
        TestSecurityContexts.loginStudent(9L, 42L, true);

        assertThatThrownBy(() -> call("portal", "/api/v1/portal/me", "/api/v1/portal/me"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.PASSWORD_CHANGE_REQUIRED_CODE);
    }

    @Test
    void staff_portalHandler_isStudentOnly_evenForAdmin() {
        TestSecurityContexts.loginAdmin(1L);

        assertThatThrownBy(() -> call("portal", "/api/v1/portal/me", "/api/v1/portal/me"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.STUDENT_ONLY_CODE);
    }

    @Test
    void parent_onlyAllowedWhereAnnotationListsParent() throws Exception {
        loginParent();

        assertThat(call("portalFamily", "/api/v1/portal/x", "/api/v1/portal/x")).isTrue();
        assertThatThrownBy(() -> call("portal", "/api/v1/portal/me", "/api/v1/portal/me"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.STUDENT_ONLY_CODE);
    }

    @Test
    void portalHandlerOutsidePortalZone_isMisconfigured() {
        TestSecurityContexts.loginStudent(9L, 42L, false);
        assertThatThrownBy(() -> call("portal", "/api/v1/auth/portal-leak", "/api/v1/auth/portal-leak"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.FORBIDDEN_CODE);
    }

    @Test
    void nonPortalHandlerInsidePortalZone_isMisconfigured() {
        TestSecurityContexts.loginStudent(9L, 42L, false);
        assertThatThrownBy(() -> call("authenticated", "/api/v1/portal/leak", "/api/v1/portal/leak"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.FORBIDDEN_CODE);
    }

    @Test
    void foreignPrincipal_isNotTreatedAsStaff() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "someone", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        assertThatThrownBy(() -> call("authenticated", "/api/v1/classes", "/api/v1/classes"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(PermissionInterceptor.STAFF_ONLY_CODE);
    }
}
