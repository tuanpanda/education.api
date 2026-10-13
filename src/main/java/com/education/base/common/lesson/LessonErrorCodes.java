package com.education.base.common.lesson;

import lombok.experimental.UtilityClass;

/**
 * Mã lỗi học liệu GĐ3 ({@code docs/portal-p3-api.md} §7). Mã kết thúc {@code _NOT_FOUND} -> 404 (GlobalExceptionHandler
 * hiện có); còn lại {@code OracleBusinessException} -> 400. GĐ3 KHÔNG thêm mã 409 (quyết định ghép GĐ2: chỉ
 * GRADE_CONFLICT là 409).
 * <p>
 * Hợp đồng Luồng 0. Bot 1 được THÊM mã; đổi/bỏ phải qua §9.
 */
@UtilityClass
public class LessonErrorCodes {

    /** 404. Khoá học không tồn tại / đã xoá. */
    public static final String COURSE_NOT_FOUND = "COURSE_NOT_FOUND";
    /** 400. Mã khoá trùng (không phân biệt hoa thường, trong các dòng chưa xoá - UX_COURSES_CODE). */
    public static final String COURSE_CODE_DUPLICATE = "COURSE_CODE_DUPLICATE";
    /** 400. Xoá khoá đang gắn lớp (EDU_CLASSES.COURSE_ID) - phải gỡ lớp hoặc ARCHIVED. */
    public static final String COURSE_IN_USE = "COURSE_IN_USE";
    /** 400. Gắn lớp vào khoá ARCHIVED / lớp đã gắn khoá khác / chép sang lớp không hợp lệ. */
    public static final String COURSE_INVALID_STATE = "COURSE_INVALID_STATE";

    /** 404. Chương không tồn tại / đã xoá / ngoài phạm vi. */
    public static final String LESSON_SECTION_NOT_FOUND = "LESSON_SECTION_NOT_FOUND";
    /** 400. Xoá chương còn bài chưa xoá. */
    public static final String LESSON_SECTION_NOT_EMPTY = "LESSON_SECTION_NOT_EMPTY";

    /** 404. Bài học không tồn tại / đã xoá / ngoài phạm vi / (cổng) chưa mở hoặc DRAFT. */
    public static final String LESSON_NOT_FOUND = "LESSON_NOT_FOUND";
    /** 400. Dữ liệu bài không hợp lệ (chuyển chương khác chủ, quá giới hạn...). */
    public static final String LESSON_INVALID = "LESSON_INVALID";
    /** 400. Chuyển trạng thái sai (publish bài đã PUBLISHED...). */
    public static final String LESSON_INVALID_STATE = "LESSON_INVALID_STATE";
    /** 400. sessionId không thuộc lớp của bài / bài thuộc khoá mẫu / buổi CANCELLED. */
    public static final String LESSON_SESSION_INVALID = "LESSON_SESSION_INVALID";
    /** 400. Danh sách sắp xếp thiếu/thừa/khác chủ. */
    public static final String LESSON_REORDER_INVALID = "LESSON_REORDER_INVALID";

    /** 404. Tài nguyên không tồn tại / không thuộc bài. */
    public static final String LESSON_RESOURCE_NOT_FOUND = "LESSON_RESOURCE_NOT_FOUND";
    /** 400. Tài nguyên không hợp lệ (thiếu url, sai loại, quá MAX_RESOURCES_PER_LESSON). */
    public static final String LESSON_RESOURCE_INVALID = "LESSON_RESOURCE_INVALID";
    /** 400. URL video ngoài allow-list hoặc không nhận dạng được id video. */
    public static final String LESSON_VIDEO_URL_NOT_ALLOWED = "LESSON_VIDEO_URL_NOT_ALLOWED";
    /** 400. URL không phải http/https hợp lệ (chặn javascript:, data:, file:). */
    public static final String LESSON_URL_INVALID = "LESSON_URL_INVALID";
    /** 400. Gắn bài tập khác lớp / bài tập đã xoá / bài học thuộc khoá mẫu. */
    public static final String LESSON_ASSIGNMENT_INVALID = "LESSON_ASSIGNMENT_INVALID";

    /** 403. Giáo viên không dạy lớp (cùng ý nghĩa NOT_CLASS_TEACHER của TeachingAssignmentGuard). */
    public static final String NOT_CLASS_TEACHER = "NOT_CLASS_TEACHER";
}
