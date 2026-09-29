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

    /** Chỉ buổi {@code PRESENT} mới được tính vào phiếu học phí. */
    public static final String ATTENDANCE_PRESENT = "PRESENT";

    /** Đi trễ vẫn được tính là có mặt khi thống kê buổi học. */
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
