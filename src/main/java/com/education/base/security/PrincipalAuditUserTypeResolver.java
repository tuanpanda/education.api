package com.education.base.security;

import com.education.base.audit.AuditUserTypeResolver;
import org.springframework.stereotype.Component;

/**
 * Nối stream A với nhật ký hệ thống (stream B): {@code SYS_AUDIT_LOGS.USER_TYPE} = loại tài khoản của người thao tác.
 * <p>
 * {@link AuthUserPrincipal#getUserType()} được {@code AccessControlServiceImpl} dựng lại từ
 * {@code SYS_USERS.USER_TYPE} (V17_1) ở MỖI request ({@code JwtAuthenticationFilter}), nên giá trị luôn khớp CSDL -
 * không tin claim {@code user_type} của JWT. Principal không có loại (không thể xảy ra với principal do filter dựng)
 * -> {@code null}.
 */
@Component
public class PrincipalAuditUserTypeResolver implements AuditUserTypeResolver {

    @Override
    public String userTypeOf(AuthUserPrincipal principal) {
        if (principal == null || principal.getUserType() == null) {
            return null;
        }
        return principal.getUserType().name();
    }
}
