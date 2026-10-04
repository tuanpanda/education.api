package com.education.base.audit;

import lombok.experimental.UtilityClass;

/**
 * Mã hành động ({@code SYS_AUDIT_LOGS.ACTION}, tối đa 50 ký tự) và loại đối tượng ({@code RESOURCE_TYPE}).
 * <p>
 * Các hành động "đã nối dây" sẵn được {@link AuditTrailAspect} ghi tự động. Nhóm "dành cho cổng học sinh" là
 * hook để stream A (tài khoản học sinh, liên kết HS, hàng rào {@code /portal}) gọi
 * {@code AuditService#record(AuditEvent)} khi triển khai - không cần sửa lớp này nếu dùng mã riêng.
 */
@UtilityClass
public class AuditActions {

    // ---- Xác thực (AuthService) ----------------------------------------------------------------
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    /** Dùng lại refresh token đã bị xoay vòng: toàn bộ phiên bị thu hồi (dấu hiệu token bị đánh cắp). */
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";

    // ---- Quản trị người dùng (UserAdminService) ------------------------------------------------
    public static final String ACCOUNT_CREATED = "ACCOUNT_CREATED";
    public static final String ACCOUNT_UPDATED = "ACCOUNT_UPDATED";
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String USER_ROLES_CHANGED = "USER_ROLES_CHANGED";

    // ---- Vai trò & phân quyền (RoleAdminService) -----------------------------------------------
    public static final String ROLE_CREATED = "ROLE_CREATED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";
    public static final String ROLE_DELETED = "ROLE_DELETED";
    public static final String ROLE_PERMISSIONS_CHANGED = "ROLE_PERMISSIONS_CHANGED";

    // ---- Tài chính (PaymentService, TuitionFeeService) -----------------------------------------
    public static final String PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";
    public static final String PAYMENT_VOIDED = "PAYMENT_VOIDED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";
    public static final String FEE_CANCELLED = "FEE_CANCELLED";
    public static final String FEE_DELETED = "FEE_DELETED";

    // ---- Dành cho cổng học sinh (stream A / các giai đoạn sau) - CHƯA có nơi gọi -----------------
    public static final String STUDENT_ACCOUNT_PROVISIONED = "STUDENT_ACCOUNT_PROVISIONED";
    public static final String STUDENT_LINK_CREATED = "STUDENT_LINK_CREATED";
    public static final String STUDENT_LINK_REMOVED = "STUDENT_LINK_REMOVED";
    public static final String PORTAL_ACCESS_DENIED = "PORTAL_ACCESS_DENIED";
    public static final String FILE_DOWNLOADED = "FILE_DOWNLOADED";

    // ---- Loại đối tượng (RESOURCE_TYPE) ----------------------------------------------------------
    public static final String RESOURCE_USER = "USER";
    public static final String RESOURCE_ROLE = "ROLE";
    public static final String RESOURCE_PAYMENT_TRANSACTION = "PAYMENT_TRANSACTION";
    public static final String RESOURCE_TUITION_FEE = "TUITION_FEE";
    public static final String RESOURCE_STUDENT = "STUDENT";
    public static final String RESOURCE_FILE = "FILE";
}
