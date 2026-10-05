package com.education.base.security;

import lombok.experimental.UtilityClass;

/**
 * Mã quyền dạng {@code MENU_CODE:FUNCTION_CODE}, khớp dữ liệu {@code SYS_MENUS} + {@code SYS_FUNCTIONS}
 * (xem các migration V1, V6, V8, V11, V12, V14_x, V16_x).
 * <p>
 * Dùng làm giá trị cho {@link RequirePermission}. Khi thêm menu/chức năng mới trong Database, khai báo
 * hằng số tương ứng tại đây rồi gắn lên Controller.
 */
@UtilityClass
public class Permissions {

    /** Vai trò quản trị tối cao: luôn được phép mọi chức năng. */
    public static final String ADMIN_ROLE = "ROLE_ADMIN";

    /** Vai trò giảng viên: chỉ được ghi điểm / điểm danh cho lớp mình phụ trách. */
    public static final String TEACHER_ROLE = "ROLE_TEACHER";

    /**
     * Vai trò học sinh (V17_1). Chỉ gán cho tài khoản {@code USER_TYPE = STUDENT}; không mang quyền menu nào -
     * học sinh chỉ dùng {@code /api/v1/portal/**} ({@link PortalAccess}).
     */
    public static final String STUDENT_ROLE = "ROLE_STUDENT";

    /** Vai trò phụ huynh (dự phòng giai đoạn sau, CHƯA seed). */
    public static final String PARENT_ROLE = "ROLE_PARENT";

    public static final String SEPARATOR = ":";

    public static final String VIEW = "VIEW";
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String EXPORT = "EXPORT";
    public static final String IMPORT = "IMPORT";

    // ---- Đào tạo --------------------------------------------------------------
    public static final String STUDENT_VIEW = "MENU_STUDENT_LIST:VIEW";
    public static final String STUDENT_CREATE = "MENU_STUDENT_LIST:CREATE";
    public static final String STUDENT_UPDATE = "MENU_STUDENT_LIST:UPDATE";
    public static final String STUDENT_DELETE = "MENU_STUDENT_LIST:DELETE";
    public static final String STUDENT_IMPORT = "MENU_STUDENT_LIST:IMPORT";

    public static final String CLASS_VIEW = "MENU_CLASS_LIST:VIEW";
    public static final String CLASS_CREATE = "MENU_CLASS_LIST:CREATE";
    public static final String CLASS_UPDATE = "MENU_CLASS_LIST:UPDATE";
    public static final String CLASS_DELETE = "MENU_CLASS_LIST:DELETE";

    public static final String ATTENDANCE_VIEW = "MENU_ATTENDANCE:VIEW";
    public static final String ATTENDANCE_CREATE = "MENU_ATTENDANCE:CREATE";
    public static final String ATTENDANCE_UPDATE = "MENU_ATTENDANCE:UPDATE";

    public static final String GRADE_VIEW = "MENU_GRADE:VIEW";
    public static final String GRADE_CREATE = "MENU_GRADE:CREATE";
    public static final String GRADE_UPDATE = "MENU_GRADE:UPDATE";

    public static final String TIMETABLE_VIEW = "MENU_TIMETABLE:VIEW";
    public static final String TIMETABLE_CONFIG_SCHEDULE = "MENU_TIMETABLE:CONFIG_SCHEDULE";
    public static final String TIMETABLE_GENERATE_SESSIONS = "MENU_TIMETABLE:GENERATE_SESSIONS";
    public static final String TIMETABLE_CANCEL_SESSION = "MENU_TIMETABLE:CANCEL_SESSION";

    // ---- Tuyển sinh -----------------------------------------------------------
    public static final String LEAD_VIEW = "MENU_LEAD_LIST:VIEW";
    public static final String LEAD_CREATE = "MENU_LEAD_LIST:CREATE";
    public static final String LEAD_UPDATE = "MENU_LEAD_LIST:UPDATE";
    public static final String LEAD_DELETE = "MENU_LEAD_LIST:DELETE";
    public static final String LEAD_CONVERT = "MENU_LEAD_LIST:CONVERT";

    // ---- Tài chính ------------------------------------------------------------
    public static final String TUITION_FEE_VIEW = "MENU_TUITION_FEE:VIEW";
    public static final String TUITION_FEE_CREATE = "MENU_TUITION_FEE:CREATE";
    public static final String TUITION_FEE_UPDATE = "MENU_TUITION_FEE:UPDATE";
    /**
     * Sinh VietQR cho khoản phí. V16_1 chuyển GEN_QR từ menu cũ {@code MENU_TUITION_PAYMENT}
     * ("Thu học phí VietQR", {@code /finance/vietqr}, trùng màn hình khoản học phí; V16_2 gỡ menu đó).
     */
    public static final String TUITION_FEE_GEN_QR = "MENU_TUITION_FEE:GEN_QR";

    public static final String PAYMENT_HISTORY_VIEW = "MENU_PAYMENT_HISTORY:VIEW";
    public static final String PAYMENT_HISTORY_CREATE = "MENU_PAYMENT_HISTORY:CREATE";
    public static final String PAYMENT_HISTORY_APPROVE = "MENU_PAYMENT_HISTORY:APPROVE";

    // ---- Tài chính - Stream A: tính phí, khoản học phí, miễn giảm (feat/fin-billing, V14_1) ---------
    // Chỉ Stream A sửa khối này. MENU_TUITION_FEE:DELETE đã seed từ V1; CANCEL và MENU_FEE_DISCOUNT do V14_1 seed.
    public static final String TUITION_FEE_DELETE = "MENU_TUITION_FEE:DELETE";
    public static final String TUITION_FEE_CANCEL = "MENU_TUITION_FEE:CANCEL";

    public static final String FEE_DISCOUNT_VIEW = "MENU_FEE_DISCOUNT:VIEW";
    public static final String FEE_DISCOUNT_CREATE = "MENU_FEE_DISCOUNT:CREATE";
    public static final String FEE_DISCOUNT_UPDATE = "MENU_FEE_DISCOUNT:UPDATE";
    public static final String FEE_DISCOUNT_DELETE = "MENU_FEE_DISCOUNT:DELETE";

    // ---- Tài chính - Stream B: giao dịch, phiếu thu, hủy / hoàn tiền (feat/fin-payments, V14_2) -------
    // Chỉ Stream B sửa khối này. VOID / REFUND trên MENU_PAYMENT_HISTORY do V14_2 seed.
    public static final String PAYMENT_HISTORY_VOID = "MENU_PAYMENT_HISTORY:VOID";
    public static final String PAYMENT_HISTORY_REFUND = "MENU_PAYMENT_HISTORY:REFUND";

    // ---- Tài chính - Stream C: báo cáo, dashboard, xuất Excel (feat/fin-reports, V14_3) ---------------
    // Chỉ Stream C sửa khối này. EXPORT trên MENU_TUITION_FEE / MENU_PAYMENT_HISTORY / MENU_DASHBOARD đã seed từ V1;
    // MENU_FINANCE_DASHBOARD và MENU_FINANCE_REPORT do V14_3 seed.
    public static final String TUITION_FEE_EXPORT = "MENU_TUITION_FEE:EXPORT";
    public static final String PAYMENT_HISTORY_EXPORT = "MENU_PAYMENT_HISTORY:EXPORT";
    public static final String DASHBOARD_EXPORT = "MENU_DASHBOARD:EXPORT";

    public static final String FINANCE_DASHBOARD_VIEW = "MENU_FINANCE_DASHBOARD:VIEW";
    public static final String FINANCE_REPORT_VIEW = "MENU_FINANCE_REPORT:VIEW";
    public static final String FINANCE_REPORT_EXPORT = "MENU_FINANCE_REPORT:EXPORT";

    // ---- Tài chính: cấu hình STK (không thuộc stream nào) ---------------------------------------------
    public static final String BANK_ACCOUNT_VIEW = "MENU_BANK_ACCOUNT:VIEW";
    public static final String BANK_ACCOUNT_CREATE = "MENU_BANK_ACCOUNT:CREATE";
    public static final String BANK_ACCOUNT_UPDATE = "MENU_BANK_ACCOUNT:UPDATE";
    public static final String BANK_ACCOUNT_DELETE = "MENU_BANK_ACCOUNT:DELETE";

    // ---- Tài liệu & Báo cáo ---------------------------------------------------
    public static final String FILE_VIEW = "MENU_FILE_EXPLORER:VIEW";
    public static final String FILE_UPLOAD = "MENU_FILE_EXPLORER:UPLOAD";
    public static final String FILE_DOWNLOAD = "MENU_FILE_EXPLORER:DOWNLOAD";

    public static final String DASHBOARD_VIEW = "MENU_DASHBOARD:VIEW";

    // ---- Quản trị hệ thống ----------------------------------------------------
    public static final String USER_VIEW = "MENU_USER_LIST:VIEW";
    public static final String USER_CREATE = "MENU_USER_LIST:CREATE";
    public static final String USER_UPDATE = "MENU_USER_LIST:UPDATE";
    public static final String USER_DELETE = "MENU_USER_LIST:DELETE";

    public static final String ROLE_VIEW = "MENU_ROLE_LIST:VIEW";
    public static final String ROLE_CREATE = "MENU_ROLE_LIST:CREATE";
    public static final String ROLE_UPDATE = "MENU_ROLE_LIST:UPDATE";
    public static final String ROLE_DELETE = "MENU_ROLE_LIST:DELETE";

    /** Nhật ký hệ thống (Giai đoạn 0 stream B, V17_3). */
    public static final String AUDIT_LOG_VIEW = "MENU_AUDIT_LOG:VIEW";

    public static final String MENU_CONFIG_VIEW = "MENU_MENU_CONFIG:VIEW";
    public static final String MENU_CONFIG_CREATE = "MENU_MENU_CONFIG:CREATE";
    public static final String MENU_CONFIG_UPDATE = "MENU_MENU_CONFIG:UPDATE";
    public static final String MENU_CONFIG_DELETE = "MENU_MENU_CONFIG:DELETE";

    // ---- Cổng học sinh - Stream A (feat/portal-p0-core, V17_2) ------------------------------------------
    // Màn hình quản trị "Tài khoản học sinh" (/system/student-accounts, dưới DIR_SYSTEM).
    public static final String STUDENT_ACCOUNT_VIEW = "MENU_STUDENT_ACCOUNT:VIEW";
    public static final String STUDENT_ACCOUNT_CREATE = "MENU_STUDENT_ACCOUNT:CREATE";
    public static final String STUDENT_ACCOUNT_RESET_PASSWORD = "MENU_STUDENT_ACCOUNT:RESET_PASSWORD";
    public static final String STUDENT_ACCOUNT_LOCK = "MENU_STUDENT_ACCOUNT:LOCK";

// ---- Cong hoc sinh - Stream B thong bao (feat/portal-p1-announce, V18_2) ---------------------
    // Menu MENU_ANNOUNCEMENT (/academic/announcements, duoi DIR_ACADEMIC).
    public static final String ANNOUNCEMENT_VIEW = "MENU_ANNOUNCEMENT:VIEW";
    public static final String ANNOUNCEMENT_CREATE = "MENU_ANNOUNCEMENT:CREATE";
    public static final String ANNOUNCEMENT_UPDATE = "MENU_ANNOUNCEMENT:UPDATE";
    public static final String ANNOUNCEMENT_DELETE = "MENU_ANNOUNCEMENT:DELETE";
    public static final String ANNOUNCEMENT_PUBLISH = "MENU_ANNOUNCEMENT:PUBLISH";


    /** Ghép mã quyền phẳng từ mã menu và mã chức năng. */
    public static String of(String menuCode, String functionCode) {
        return menuCode + SEPARATOR + functionCode;
    }
}
