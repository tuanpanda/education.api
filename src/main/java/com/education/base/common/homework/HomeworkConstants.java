package com.education.base.common.homework;

import lombok.experimental.UtilityClass;

import java.time.ZoneId;

/**
 * Hằng số nghiệp vụ bài tập về nhà (GĐ2, bảng {@code LMS_ASSIGNMENTS} / {@code LMS_SUBMISSIONS} /
 * {@code LMS_SUBMISSION_HISTORY} - V20_1, V20_2). Hợp đồng: {@code docs/homework-api.md}.
 * <p>
 * Hợp đồng Luồng 0. Bot 1 (backend) được THÊM hằng số mới; đổi giá trị hiện có phải qua mục
 * "Yêu cầu thay đổi hợp đồng".
 */
@UtilityClass
public class HomeworkConstants {

    /** Hạn nộp luôn so bằng giờ server theo múi này (cột TIMESTAMP không múi giờ). */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** {@code LMS_ASSIGNMENTS.SUBMISSION_TYPE} ({@code CK_ASG_SUB_TYPE}). */
    @UtilityClass
    public static class SubmissionType {
        public static final String TEXT = "TEXT";
        public static final String FILE = "FILE";
        public static final String BOTH = "BOTH";
        public static final String PATTERN = "TEXT|FILE|BOTH";
    }

    /** {@code LMS_ASSIGNMENTS.STATUS} ({@code CK_ASG_STATUS}). DRAFT -> PUBLISHED -> CLOSED (CLOSED -> PUBLISHED = mở lại). */
    @UtilityClass
    public static class AssignmentStatus {
        public static final String DRAFT = "DRAFT";
        public static final String PUBLISHED = "PUBLISHED";
        public static final String CLOSED = "CLOSED";
        public static final String PATTERN = "DRAFT|PUBLISHED|CLOSED";
    }

    /** {@code LMS_SUBMISSIONS.STATUS} ({@code CK_SUB_STATUS}). */
    @UtilityClass
    public static class SubmissionStatus {
        public static final String DRAFT = "DRAFT";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String RETURNED = "RETURNED";
        public static final String GRADED = "GRADED";
    }

    /**
     * Trạng thái hiển thị trong ma trận bài nộp (tính, không lưu): HS chưa có dòng submission hoặc dòng DRAFT
     * = {@code NOT_SUBMITTED}; quá hạn mà chưa nộp = {@code MISSING}.
     */
    @UtilityClass
    public static class MatrixState {
        public static final String NOT_SUBMITTED = "NOT_SUBMITTED";
        public static final String MISSING = "MISSING";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String LATE = "LATE";
        public static final String RETURNED = "RETURNED";
        public static final String GRADED = "GRADED";
    }

    /** Tab cổng học sinh {@code GET /api/v1/portal/me/assignments?tab=}. */
    @UtilityClass
    public static class PortalTab {
        public static final String TODO = "todo";
        public static final String SUBMITTED = "submitted";
        public static final String GRADED = "graded";
        public static final String OVERDUE = "overdue";
        public static final String PATTERN = "todo|submitted|graded|overdue";
    }

    /** {@code LMS_SUBMISSION_HISTORY.OUTCOME}. */
    @UtilityClass
    public static class HistoryOutcome {
        public static final String SUBMITTED = "SUBMITTED";
        public static final String RETURNED = "RETURNED";
        public static final String GRADED = "GRADED";
    }

    /** Loại điểm ghi vào {@code EDU_GRADES} khi đồng bộ (CK_GRADES_TYPE không có 'HOMEWORK' - xem V20_4). */
    public static final String GRADEBOOK_GRADE_TYPE = "ASSIGNMENT";

    /** Thang điểm của {@code EDU_GRADES} ({@code CK_GRADES_SCORE BETWEEN 0 AND 10}). */
    public static final int GRADEBOOK_SCALE = 10;

    /** Số ngày "sắp đến hạn" cho huy hiệu dashboard cổng. */
    public static final int DUE_SOON_DAYS = 3;

    public static final int TITLE_MAX = 200;
    public static final int RETURN_REASON_MAX = 500;
    /** Giới hạn ký tự bài làm dạng văn bản (HTML đã sanitize). */
    public static final int CONTENT_TEXT_MAX = 50_000;
    public static final int MAX_FILES_LIMIT = 10;
}
