package com.education.base.common;

import lombok.experimental.UtilityClass;

/**
 * Tập giá trị hợp lệ của các cột trạng thái, khớp 1-1 với CHECK CONSTRAINT trong Database
 * (xem {@code db/migration/V1__education_full_schema.sql}).
 * <p>
 * Mục đích: chỉ có MỘT nơi khai báo danh sách giá trị. Nếu sửa CHECK CONSTRAINT trong
 * Database thì sửa đúng hằng số tương ứng ở đây, tránh tình trạng Jakarta Validation cho
 * qua nhưng Oracle lại từ chối bằng {@code ORA-02290}.
 */
@UtilityClass
public class DomainConstants {

    /** Trạng thái học sinh - {@code EDU_STUDENTS.STATUS}. */
    public static final String STUDENT_STATUS_PATTERN = "ACTIVE|INACTIVE|GRADUATED|SUSPENDED";

    /** Chỉ học sinh {@code ACTIVE} mới được liệt kê / chọn trên giao diện. */
    public static final String STUDENT_STATUS_ACTIVE = "ACTIVE";

    /** Trạng thái lớp học - {@code CK_CLASSES_STATUS}. */
    public static final String CLASS_STATUS_PATTERN = "PLANNED|OPEN|ONGOING|CLOSED|CANCELLED";

    /** Trạng thái ghi danh - {@code CK_CLASS_STUDENTS_STATUS}. */
    public static final String ENROLLMENT_STATUS_PATTERN = "ENROLLED|COMPLETED|DROPPED";

    /** Trạng thái điểm danh - {@code CK_ATTENDANCE_STATUS}. */
    public static final String ATTENDANCE_STATUS_PATTERN = "PRESENT|ABSENT|LATE|EXCUSED";

    /**
     * Có mặt. Buổi {@code PRESENT} được tính phí khi sinh phiếu học phí tháng
     * (xem {@link #BILLABLE_ATTENDANCE_STATUSES}).
     */
    public static final String ATTENDANCE_PRESENT = "PRESENT";

    /**
     * Đi trễ: vẫn được tính là có mặt khi thống kê buổi học, và vẫn được tính phí như {@code PRESENT}
     * (xem {@link #BILLABLE_ATTENDANCE_STATUSES}).
     */
    public static final String ATTENDANCE_LATE = "LATE";

    /** Nhãn mặc định trên phiếu học phí điện tử. */
    public static final String TUITION_SLIP_LABEL_DEFAULT = "Mặc Định";

    /** Lời chúc mặc định cuối phiếu học phí. */
    public static final String TUITION_SLIP_DEFAULT_WISH = "Chúc em luôn vui vẻ và học tốt! ❤️";

    /** Loại điểm - {@code CK_GRADES_TYPE}. */
    public static final String GRADE_TYPE_PATTERN = "ASSIGNMENT|QUIZ|MIDTERM|FINAL";

    /** Nguồn tuyển sinh - {@code CK_LEADS_SOURCE}. */
    public static final String LEAD_SOURCE_PATTERN = "WEBSITE|FACEBOOK|ZALO|REFERRAL|WALK_IN|HOTLINE|OTHER";

    /** Trạng thái lead - {@code CK_LEADS_STATUS}. */
    public static final String LEAD_STATUS_PATTERN = "NEW|CONTACTED|QUALIFIED|CONVERTED|LOST";

    /** Trạng thái khoản học phí - {@code CK_FEES_STATUS}. */
    public static final String FEE_STATUS_PATTERN = "UNPAID|PARTIAL|PAID|OVERDUE|CANCELLED";

    // ---- Tài chính: dùng chung (Stream 0) ------------------------------------
    // Giá trị lẻ của CK_FEES_STATUS; FeeStatusCalculator là nơi DUY NHẤT suy ra trạng thái từ số tiền.

    public static final String FEE_STATUS_UNPAID = "UNPAID";

    public static final String FEE_STATUS_PARTIAL = "PARTIAL";

    public static final String FEE_STATUS_PAID = "PAID";

    public static final String FEE_STATUS_OVERDUE = "OVERDUE";

    public static final String FEE_STATUS_CANCELLED = "CANCELLED";

    /** Hình thức thanh toán - {@code CK_TRANS_METHOD}. */
    public static final String PAYMENT_METHOD_PATTERN = "CASH|BANK_TRANSFER|VIETQR|CARD|EWALLET";

    /** Trạng thái giao dịch - {@code CK_TRANS_STATUS}. */
    public static final String TRANSACTION_STATUS_PATTERN = "PENDING|SUCCESS|FAILED|REFUNDED";

    /**
     * Thứ trong tuần lưu DB: 2=Thứ Hai … 7=Thứ Bảy, 8=Chủ Nhật
     * ({@code CK_SCHEDULES_DOW} / {@code EDU_CLASS_SCHEDULES.DAY_OF_WEEK}).
     */
    public static final String DAY_OF_WEEK_PATTERN = "[2-8]";

    /** Giờ học dạng 24h {@code HH:mm} — {@code CK_SCHEDULES_TIME} / {@code CK_SESSIONS_TIME}. */
    public static final String TIME_HHMM_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    /** Trạng thái khung lịch tuần - {@code CK_SCHEDULES_STATUS}. */
    public static final String SCHEDULE_STATUS_PATTERN = "ACTIVE|INACTIVE";

    /** Trạng thái buổi học - {@code CK_SESSIONS_STATUS}. */
    public static final String SESSION_STATUS_PATTERN = "SCHEDULED|COMPLETED|CANCELLED";

    /**
     * Học sinh còn hiện trên danh sách/chọn lớp/điểm danh/học phí:
     * chưa xóa mềm và trạng thái {@link #STUDENT_STATUS_ACTIVE}.
     */
    public boolean isListedStudent(String status, Integer isDeleted) {
        return Integer.valueOf(0).equals(isDeleted) && STUDENT_STATUS_ACTIVE.equals(status);
    }

    /** Trạng thái tài khoản - {@code SYS_USERS.STATUS} ({@code CK_USERS_STATUS}, V12). */
    public static final String USER_STATUS_PATTERN = "ACTIVE|INACTIVE|LOCKED";

    public static final String USER_STATUS_ACTIVE = "ACTIVE";

    public static final String USER_STATUS_INACTIVE = "INACTIVE";

    public static final String USER_STATUS_LOCKED = "LOCKED";

    /** Trạng thái vai trò / menu - {@code SYS_ROLES.STATUS}, {@code SYS_MENUS.STATUS}. */
    public static final String RECORD_STATUS_ACTIVE = "ACTIVE";

    public static final String RECORD_STATUS_INACTIVE = "INACTIVE";

    /**
     * Chính sách mật khẩu: 8-72 ký tự, có ít nhất một chữ cái và một chữ số. Ngoài ra tối đa
     * {@link #PASSWORD_MAX_BYTES} byte UTF-8 (kiểm tra bằng {@code @MaxUtf8Bytes}).
     */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";

    public static final String PASSWORD_POLICY_MESSAGE =
            "Mật khẩu phải từ 8 đến 72 ký tự và có cả chữ lẫn số";

    /** BCrypt chỉ dùng 72 byte đầu của mật khẩu: dài hơn sẽ bị cắt ngầm, nên chặn ngay khi đặt mật khẩu. */
    public static final int PASSWORD_MAX_BYTES = 72;

    public static final String PASSWORD_MAX_BYTES_MESSAGE =
            "Mật khẩu không được vượt quá 72 byte (ký tự có dấu tiếng Việt chiếm 2-3 byte)";

    /** Tên đăng nhập: 3-50 ký tự chữ, số, dấu chấm, gạch dưới, gạch ngang. */
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]{3,50}$";

    /** Mã vai trò / mã menu / mã chức năng: chữ IN HOA, số, gạch dưới. */
    public static final String SYSTEM_CODE_PATTERN = "^[A-Z][A-Z0-9_]{1,49}$";

    /** Loại menu - {@code CK_MENUS_TYPE}. */
    public static final String MENU_TYPE_PATTERN = "DIR|MENU";

    public static final String MENU_TYPE_DIR = "DIR";

    public static final String MENU_TYPE_MENU = "MENU";

    // ---- Tài chính - Stream A: tính phí, khoản học phí, miễn giảm (feat/fin-billing) ----------------
    // Chỉ Stream A thêm hằng số vào khối này (ví dụ FEE_TYPE_PATTERN, DISCOUNT_TYPE_PATTERN).

    /**
     * Trạng thái điểm danh được tính phí khi sinh phiếu học phí tháng ({@code PRESENT} + {@code LATE}).
     * {@code PRC_GET_TUITION_SLIP_DATA} (V14_1) dùng cùng quy tắc cho danh sách ngày đi học trên phiếu.
     */
    public static final java.util.List<String> BILLABLE_ATTENDANCE_STATUSES =
            java.util.List.of(ATTENDANCE_PRESENT, ATTENDANCE_LATE);

    /**
     * Sinh bù phiếu cho tháng đã qua: nếu hạn thu mặc định (ngày cuối tháng) đã trước hôm nay thì hạn thu
     * = hôm nay + số ngày này, để phiếu không bị {@code OVERDUE} ngay khi vừa tạo.
     */
    public static final int FEE_BACKBILL_GRACE_DAYS = 7;

    /** Ghi chú / lý do khi hệ thống tự hủy khoản phí tháng không còn buổi tính phí (chưa thu đồng nào). */
    public static final String FEE_AUTO_CANCEL_NO_SESSION_NOTE = "auto: không còn buổi tính phí";

    /** Tiền tố lý do hủy do hệ thống tự sinh (phân biệt với hủy thủ công). */
    public static final String FEE_AUTO_CANCEL_PREFIX = "auto:";

    /** Loại miễn giảm - {@code CK_DISCOUNTS_TYPE} ({@code FIN_STUDENT_DISCOUNTS}, V14_1). */
    public static final String DISCOUNT_TYPE_PATTERN = "PERCENT|AMOUNT";

    /** Giảm theo % tổng tiền phiếu ({@code 0 < giá trị <= 100}). */
    public static final String DISCOUNT_TYPE_PERCENT = "PERCENT";

    /** Giảm một số tiền cố định cho mỗi phiếu tháng. */
    public static final String DISCOUNT_TYPE_AMOUNT = "AMOUNT";

    // ---- Tài chính - Stream B: giao dịch, phiếu thu, hủy / hoàn tiền (feat/fin-payments) -------------
    // Chỉ Stream B thêm hằng số vào khối này (ví dụ TRANSACTION_TYPE_PATTERN).

    // ---- Tài chính - Stream C: báo cáo, dashboard, xuất Excel (feat/fin-reports) ----------------------
    // Chỉ Stream C thêm hằng số vào khối này (ví dụ DEBT_AGING_BUCKETS).

    /** Tên module dùng khi lưu file đính kèm theo từng phân hệ. */
    @UtilityClass
    public static class Module {
        public static final String STUDENT = "STUDENT";
        public static final String CLASS = "CLASS";
        public static final String LEAD = "LEAD";
        public static final String TUITION = "TUITION";
        public static final String COMMON = "COMMON";
    }
}
