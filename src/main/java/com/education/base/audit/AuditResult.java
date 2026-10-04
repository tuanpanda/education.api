package com.education.base.audit;

/**
 * Kết quả của một sự kiện nhật ký ({@code SYS_AUDIT_LOGS.RESULT}, ràng buộc {@code CK_AUDIT_LOGS_RESULT} - V17_3).
 */
public enum AuditResult {

    /** Thao tác thành công. */
    SUCCESS,

    /** Thao tác thất bại (sai mật khẩu, lỗi nghiệp vụ...). */
    FAILURE,

    /** Bị từ chối do thiếu quyền / sai phạm vi dữ liệu (dành cho hàng rào cổng học sinh - stream A). */
    DENIED
}
