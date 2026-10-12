package com.education.base.common.homework;

import lombok.experimental.UtilityClass;

/**
 * Mã lỗi ({@code ApiResponse.code}) của bài tập về nhà. HTTP theo {@code GlobalExceptionHandler}:
 * {@code OracleBusinessException} -> 400 (mã kết thúc {@code _NOT_FOUND} -> 404), {@code ForbiddenException} -> 403.
 * Lỗi file (quá dung lượng, sai định dạng, quá số file...) dùng {@code FileErrorCodes} của nền tảng file.
 * Bảng đầy đủ + thông báo tiếng Việt: {@code docs/homework-api.md} mục "Mã lỗi".
 * <p>
 * Hợp đồng Luồng 0: Bot 1 được THÊM mã, không đổi tên mã đã có.
 */
@UtilityClass
public class HomeworkErrorCodes {

    /** 404. Học sinh: bài không tồn tại / không thuộc lớp đang học / chưa PUBLISHED (không lộ tồn tại). */
    public static final String ASSIGNMENT_NOT_FOUND = "ASSIGNMENT_NOT_FOUND";
    /** 404. Bài nộp không tồn tại hoặc không thuộc phạm vi. */
    public static final String SUBMISSION_NOT_FOUND = "SUBMISSION_NOT_FOUND";
    /** 400. Trạng thái bài không cho thao tác (ví dụ sửa lớp sau khi đã đăng, publish bài CLOSED...). */
    public static final String ASSIGNMENT_INVALID_STATE = "ASSIGNMENT_INVALID_STATE";
    /** 400. Dữ liệu bài không hợp lệ (LATE_UNTIL <= DUE_AT, MAX_FILES > giới hạn module...). */
    public static final String ASSIGNMENT_INVALID = "ASSIGNMENT_INVALID";
    /** 400. Buổi học không thuộc lớp / đã huỷ. */
    public static final String ASSIGNMENT_SESSION_INVALID = "ASSIGNMENT_SESSION_INVALID";
    /** 400. Xoá bài đã có bài nộp (SUBMITTED/GRADED/RETURNED) - phải CLOSED thay vì xoá. */
    public static final String ASSIGNMENT_HAS_SUBMISSIONS = "ASSIGNMENT_HAS_SUBMISSIONS";
    /** 400. Quá hạn và không cho nộp muộn / quá LATE_UNTIL / bài CLOSED. */
    public static final String SUBMISSION_DEADLINE_PASSED = "SUBMISSION_DEADLINE_PASSED";
    /** 400. Bài nộp đã GRADED hoặc SUBMITTED mà ALLOW_RESUBMIT = 0 -> không sửa/nộp lại. */
    public static final String SUBMISSION_LOCKED = "SUBMISSION_LOCKED";
    /** 400. Nộp rỗng: TEXT cần nội dung, FILE cần >= 1 file, BOTH cần ít nhất một trong hai. */
    public static final String SUBMISSION_EMPTY = "SUBMISSION_EMPTY";
    /** 400. Gửi text cho bài FILE hoặc file cho bài TEXT. */
    public static final String SUBMISSION_TYPE_MISMATCH = "SUBMISSION_TYPE_MISMATCH";
    /** 400. Số file vượt {@code LMS_ASSIGNMENTS.MAX_FILES} (giới hạn module do FileLimitService báo FILE_LIMIT_COUNT). */
    public static final String SUBMISSION_TOO_MANY_FILES = "SUBMISSION_TOO_MANY_FILES";
    /** 400. Chấm/trả bài khi bài nộp chưa SUBMITTED (DRAFT / chưa có). */
    public static final String SUBMISSION_INVALID_STATE = "SUBMISSION_INVALID_STATE";
    /** 400. Điểm < 0 hoặc > MAX_SCORE, hoặc quá 2 chữ số thập phân. */
    public static final String GRADE_SCORE_OUT_OF_RANGE = "GRADE_SCORE_OUT_OF_RANGE";
    /** 409 (Bot 1 thêm ánh xạ mã kết thúc {@code _CONFLICT} + {@code ObjectOptimisticLockingFailureException} -> 409 trong GlobalExceptionHandler). expectedVersion khác VERSION hiện tại (người khác vừa chấm). */
    public static final String GRADE_CONFLICT = "GRADE_CONFLICT";
    /** 403. Giáo viên không dạy lớp (cùng ý nghĩa NOT_CLASS_TEACHER của TeachingAssignmentGuard). */
    public static final String NOT_CLASS_TEACHER = "NOT_CLASS_TEACHER";
    /** 404. Cổng: file không thuộc học sinh / bài không nhìn thấy. */
    public static final String PORTAL_FILE_NOT_FOUND = "PORTAL_FILE_NOT_FOUND";
    /** 400. Đồng bộ sổ điểm thất bại (lớp/HS không hợp lệ). Không chặn việc chấm. */
    /** 400. Sửa/xoá tay (API điểm) dòng EDU_GRADES có ASSIGNMENT_ID. */
    public static final String GRADE_SYNCED_READONLY = "GRADE_SYNCED_READONLY";
    public static final String GRADEBOOK_SYNC_FAILED = "GRADEBOOK_SYNC_FAILED";
}
