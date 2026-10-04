package com.education.base.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalAuditUserTypeResolverTest {

    private final PrincipalAuditUserTypeResolver resolver = new PrincipalAuditUserTypeResolver();

    @Test
    void resolvesUserTypeFromPrincipal() {
        assertThat(resolver.userTypeOf(AuthUserPrincipal.builder().id(1L).username("a").build())).isEqualTo("STAFF");
        assertThat(resolver.userTypeOf(AuthUserPrincipal.builder().id(2L).username("hs1")
                .userType(UserType.STUDENT).build())).isEqualTo("STUDENT");
        assertThat(resolver.userTypeOf(AuthUserPrincipal.builder().id(3L).username("p")
                .userType(UserType.PARENT).build())).isEqualTo("PARENT");
        assertThat(resolver.userTypeOf(AuthUserPrincipal.builder().id(4L).username("x").userType(null).build()))
                .isNull();
        assertThat(resolver.userTypeOf(null)).isNull();
    }
}
