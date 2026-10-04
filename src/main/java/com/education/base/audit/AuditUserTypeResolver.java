package com.education.base.audit;

import com.education.base.security.AuthUserPrincipal;

/**
 * Hook (tùy chọn) xác định loại tài khoản ghi vào {@code SYS_AUDIT_LOGS.USER_TYPE}.
 * <p>
 * Stream A (cột {@code SYS_USERS.USER_TYPE}) chỉ cần khai báo một bean cài đặt interface này, ví dụ
 * {@code principal -> principal.getUserType()}; không có bean nào thì {@code USER_TYPE} để {@code NULL}.
 */
@FunctionalInterface
public interface AuditUserTypeResolver {

    /** Loại tài khoản (STAFF / STUDENT / PARENT...) của {@code principal}; {@code null} nếu không xác định. */
    String userTypeOf(AuthUserPrincipal principal);
}
