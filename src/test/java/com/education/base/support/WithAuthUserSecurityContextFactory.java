package com.education.base.support;

import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.UserType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;
import java.util.Set;

public class WithAuthUserSecurityContextFactory implements WithSecurityContextFactory<WithAuthUser> {

    @Override
    public SecurityContext createSecurityContext(WithAuthUser annotation) {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(annotation.id())
                .username(annotation.username())
                .fullName(annotation.username())
                .roles(List.of(annotation.roles()))
                .permissions(Set.of(annotation.permissions()))
                .mustChangePassword(annotation.mustChangePassword())
                .userType(UserType.valueOf(annotation.userType()))
                .studentId(annotation.studentId() < 0 ? null : annotation.studentId())
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        return context;
    }
}
