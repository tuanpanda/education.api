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
    /** Tải file (kể cả từ cổng học sinh). Từ V19 được Luồng A ghi ở {@code /files/download}. */
    public static final String FILE_DOWNLOADED = "FILE_DOWNLOADED";

    // ---- File core - Luồng A (feat/files-core) ----------------------------------------------------------
    // Chỉ Luồng A sửa khối này. RESOURCE_TYPE = FILE, RESOURCE_ID = fileId; detail: module, refId, size, ext
    // (không ghi đường dẫn tuyệt đối).
    public static final String FILE_UPLOADED = "FILE_UPLOADED";
    /** Chỉ ghi khi {@code app.files.audit.views=true} (QĐ-F11). */
    public static final String FILE_VIEWED = "FILE_VIEWED";
    public static final String FILE_DELETED = "FILE_DELETED";
    /** Lý do trong detail: TYPE / SIZE / COUNT / TOTAL / QUOTA / DISK / VIRUS / REFERENCE. */
    public static final String FILE_UPLOAD_REJECTED = "FILE_UPLOAD_REJECTED";
    public static final String FILE_ACCESS_DENIED = "FILE_ACCESS_DENIED";
    public static final String FILE_INFECTED = "FILE_INFECTED";

    // ---- Quản lý file (admin, job) - Luồng B (feat/files-admin) ------------------------------------------
    // Chỉ Luồng B sửa khối này. Job dọn rác ghi actor SYSTEM.
    public static final String FILE_RESTORED = "FILE_RESTORED";
    public static final String FILE_PURGED = "FILE_PURGED";
    public static final String FILE_SETTINGS_CHANGED = "FILE_SETTINGS_CHANGED";
    public static final String FILE_QUOTA_CHANGED = "FILE_QUOTA_CHANGED";
    public static final String FILE_SCAN_STARTED = "FILE_SCAN_STARTED";
    public static final String FILE_SCAN_ITEM_RESOLVED = "FILE_SCAN_ITEM_RESOLVED";
    public static final String FILE_CHECKSUM_BACKFILL_STARTED = "FILE_CHECKSUM_BACKFILL_STARTED";

    // ---- Kho tài liệu - Luồng D (feat/files-docs) --------------------------------------------------------
    // Chỉ Luồng D sửa khối này. RESOURCE_TYPE = DOC_FOLDER / DOC_ITEM. Tải file của mục ghi FILE_DOWNLOADED (Luồng A).
    public static final String DOC_FOLDER_CREATED = "DOC_FOLDER_CREATED";
    public static final String DOC_FOLDER_UPDATED = "DOC_FOLDER_UPDATED";
    public static final String DOC_FOLDER_MOVED = "DOC_FOLDER_MOVED";
    public static final String DOC_FOLDER_DELETED = "DOC_FOLDER_DELETED";
    public static final String DOC_ITEM_CREATED = "DOC_ITEM_CREATED";
    public static final String DOC_ITEM_UPDATED = "DOC_ITEM_UPDATED";
    public static final String DOC_ITEM_MOVED = "DOC_ITEM_MOVED";
    public static final String DOC_ITEM_FILE_REPLACED = "DOC_ITEM_FILE_REPLACED";
    public static final String DOC_ITEM_DELETED = "DOC_ITEM_DELETED";
    /** Detail: đích cũ -> mới (targetType:targetId, visibleToStudents). */
    public static final String DOC_SHARES_CHANGED = "DOC_SHARES_CHANGED";

    // ---- GĐ2 Bài tập về nhà (hợp đồng Luồng 0; chỉ Bot 1 thêm dòng mới vào khối này) ----------------
    public static final String HOMEWORK_ASSIGNMENT_CREATED = "HOMEWORK_ASSIGNMENT_CREATED";
    public static final String HOMEWORK_ASSIGNMENT_UPDATED = "HOMEWORK_ASSIGNMENT_UPDATED";
    public static final String HOMEWORK_ASSIGNMENT_PUBLISHED = "HOMEWORK_ASSIGNMENT_PUBLISHED";
    public static final String HOMEWORK_ASSIGNMENT_CLOSED = "HOMEWORK_ASSIGNMENT_CLOSED";
    public static final String HOMEWORK_ASSIGNMENT_REOPENED = "HOMEWORK_ASSIGNMENT_REOPENED";
    public static final String HOMEWORK_ASSIGNMENT_DELETED = "HOMEWORK_ASSIGNMENT_DELETED";
    /** Học sinh bấm Nộp. Detail: assignmentId, attemptNo, late, fileCount. */
    public static final String HOMEWORK_SUBMITTED = "HOMEWORK_SUBMITTED";
    /** Detail: assignmentId, studentId, oldScore, newScore, version. */
    public static final String HOMEWORK_GRADED = "HOMEWORK_GRADED";
    public static final String HOMEWORK_RETURNED = "HOMEWORK_RETURNED";
    public static final String HOMEWORK_EXPORTED = "HOMEWORK_EXPORTED";
    /** Đồng bộ sổ điểm lỗi (không chặn chấm). */
    public static final String HOMEWORK_GRADEBOOK_SYNC_FAILED = "HOMEWORK_GRADEBOOK_SYNC_FAILED";

    // ---- Loại đối tượng (RESOURCE_TYPE) ----------------------------------------------------------
    public static final String RESOURCE_USER = "USER";
    public static final String RESOURCE_ROLE = "ROLE";
    public static final String RESOURCE_PAYMENT_TRANSACTION = "PAYMENT_TRANSACTION";
    public static final String RESOURCE_TUITION_FEE = "TUITION_FEE";
    public static final String RESOURCE_STUDENT = "STUDENT";
    public static final String RESOURCE_FILE = "FILE";
    // Kho tài liệu - Chỉ Luồng D sửa 2 dòng này.
    public static final String RESOURCE_DOC_FOLDER = "DOC_FOLDER";
    public static final String RESOURCE_DOC_ITEM = "DOC_ITEM";
    // Đợt đối soát - Chỉ Luồng B sửa dòng này.
    public static final String RESOURCE_FILE_SCAN_RUN = "FILE_SCAN_RUN";
    // GĐ2 Bài tập về nhà.
    public static final String RESOURCE_ASSIGNMENT = "ASSIGNMENT";
    public static final String RESOURCE_SUBMISSION = "SUBMISSION";
}
