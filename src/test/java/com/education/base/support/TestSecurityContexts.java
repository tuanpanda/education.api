package com.education.base.support;

import com.education.base.security.AuthUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

/**
 * Gắn / gỡ người dùng đăng nhập giả lập vào {@link SecurityContextHolder} cho unit test tầng Service
 * (không có Spring context). Nhớ gọi {@link #clear()} trong {@code @AfterEach}.
 */
public final class TestSecurityContexts {

    private TestSecurityContexts() {
    }

    public static AuthUserPrincipal loginAdmin(long id) {
        return login(id, List.of("ROLE_ADMIN"), Set.of());
    }

    public static AuthUserPrincipal login(long id, List<String> roles, Set<String> permissions) {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(id)
                .username("user" + id)
                .fullName("User " + id)
                .roles(roles)
                .permissions(permissions)
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        return principal;
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
