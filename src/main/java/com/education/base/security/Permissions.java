package com.education.base.security;

import lombok.experimental.UtilityClass;

/**
 * Mã quyền dạng {@code MENU_CODE:FUNCTION_CODE}, khớp dữ liệu {@code SYS_MENUS} + {@code SYS_FUNCTIONS}
 * (xem các migration V1, V6, V8, V11, V12).
 * <p>
 * Dùng làm giá trị cho {@link RequirePermission}. Khi thêm menu/chức năng mới trong Database, khai báo
 * hằng số tương ứng tại đây rồi gắn lên Controller.
 */
@UtilityClass
public class Permissions {

    /** Vai trò quản trị tối cao: luôn được phép mọi chức năng. */
    public static final String ADMIN_ROLE = "ROLE_ADMIN";

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
    public static final String TUITION_PAYMENT_VIEW = "MENU_TUITION_PAYMENT:VIEW";
    public static final String TUITION_PAYMENT_GEN_QR = "MENU_TUITION_PAYMENT:GEN_QR";

    public static final String TUITION_FEE_VIEW = "MENU_TUITION_FEE:VIEW";
    public static final String TUITION_FEE_CREATE = "MENU_TUITION_FEE:CREATE";
    public static final String TUITION_FEE_UPDATE = "MENU_TUITION_FEE:UPDATE";

    public static final String PAYMENT_HISTORY_VIEW = "MENU_PAYMENT_HISTORY:VIEW";
    public static final String PAYMENT_HISTORY_CREATE = "MENU_PAYMENT_HISTORY:CREATE";
    public static final String PAYMENT_HISTORY_APPROVE = "MENU_PAYMENT_HISTORY:APPROVE";

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

    public static final String MENU_CONFIG_VIEW = "MENU_MENU_CONFIG:VIEW";
    public static final String MENU_CONFIG_CREATE = "MENU_MENU_CONFIG:CREATE";
    public static final String MENU_CONFIG_UPDATE = "MENU_MENU_CONFIG:UPDATE";
    public static final String MENU_CONFIG_DELETE = "MENU_MENU_CONFIG:DELETE";

    /** Ghép mã quyền phẳng từ mã menu và mã chức năng. */
    public static String of(String menuCode, String functionCode) {
        return menuCode + SEPARATOR + functionCode;
    }
}
