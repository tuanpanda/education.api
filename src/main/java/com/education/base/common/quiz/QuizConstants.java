package com.education.base.common.quiz;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

/**
 * Hằng số nghiệp vụ trắc nghiệm / kiểm tra online (GĐ4, V22_1..V22_4). Hợp đồng: {@code docs/contracts/quiz-api.md} §0.
 */
@UtilityClass
public class QuizConstants {

    /** Giờ server (cột TIMESTAMP không múi giờ). */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Ân hạn mạng (giây) cho lưu/nộp sau DEADLINE_AT. */
    public static final int GRACE_SEC = 30;
    /** Số lần lưu đáp án tối đa / attempt / cửa sổ. */
    public static final int SAVE_RATE_LIMIT = 60;
    public static final int SAVE_RATE_WINDOW_SEC = 60;
    public static final int IMPORT_MAX_ROWS = 500;
    public static final long IMPORT_MAX_BYTES = 2L * 1024 * 1024;
    public static final int DURATION_MIN = 1;
    public static final int DURATION_MAX = 300;
    public static final int MAX_ATTEMPTS_MIN = 1;
    public static final int MAX_ATTEMPTS_MAX = 10;
    public static final int OPTIONS_MIN = 2;
    public static final int OPTIONS_MAX = 10;
    public static final int ACCEPTED_ANSWERS_MAX = 10;
    public static final int CONTENT_MAX = 20_000;
    public static final int TEXT_ANSWER_MAX = 10_000;
    public static final int TITLE_MAX = 200;
    public static final int PREVIEW_MAX = 200;
    public static final int REASON_MIN = 5;
    public static final int REASON_MAX = 500;
    /** Bước điểm. */
    public static final BigDecimal SCORE_STEP = new BigDecimal("0.25");
    public static final int SCALE10 = 10;
    /** Loại điểm ghi vào EDU_GRADES (CK_GRADES_TYPE đã có 'QUIZ'). */
    public static final String GRADEBOOK_GRADE_TYPE = "QUIZ";
    public static final int MAX_PAGE_SIZE = 100;
    /** Chia lô truy vấn IN. */
    public static final int IN_BATCH = 900;

    @UtilityClass
    public static class QuestionType {
        public static final String SINGLE = "SINGLE";
        public static final String MULTI = "MULTI";
        public static final String TRUE_FALSE = "TRUE_FALSE";
        public static final String SHORT_TEXT = "SHORT_TEXT";
        public static final String ESSAY = "ESSAY";
        public static final List<String> ALL = List.of(SINGLE, MULTI, TRUE_FALSE, SHORT_TEXT, ESSAY);
        public static final String PATTERN = "SINGLE|MULTI|TRUE_FALSE|SHORT_TEXT|ESSAY";
    }

    @UtilityClass
    public static class Visibility {
        public static final String PRIVATE = "PRIVATE";
        public static final String SHARED = "SHARED";
        public static final String PATTERN = "PRIVATE|SHARED";
    }

    @UtilityClass
    public static class QuizStatus {
        public static final String DRAFT = "DRAFT";
        public static final String PUBLISHED = "PUBLISHED";
        public static final String CLOSED = "CLOSED";
    }

    @UtilityClass
    public static class ResultMode {
        public static final String IMMEDIATE = "IMMEDIATE";
        public static final String AFTER_CLOSE = "AFTER_CLOSE";
        public static final String MANUAL = "MANUAL";
        public static final String PATTERN = "IMMEDIATE|AFTER_CLOSE|MANUAL";
    }

    @UtilityClass
    public static class ScorePolicy {
        public static final String HIGHEST = "HIGHEST";
        public static final String LAST = "LAST";
        public static final String AVERAGE = "AVERAGE";
        public static final String PATTERN = "HIGHEST|LAST|AVERAGE";
    }

    @UtilityClass
    public static class AttemptStatus {
        public static final String IN_PROGRESS = "IN_PROGRESS";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String AUTO_SUBMITTED = "AUTO_SUBMITTED";
        public static final String GRADED = "GRADED";
        /** Ma trận: HS chưa làm (tính, không lưu). */
        public static final String NOT_STARTED = "NOT_STARTED";
        public static final List<String> FINISHED = List.of(SUBMITTED, AUTO_SUBMITTED, GRADED);
    }

    @UtilityClass
    public static class PortalTab {
        public static final String UPCOMING = "UPCOMING";
        public static final String OPEN = "OPEN";
        public static final String DONE = "DONE";
    }

    @UtilityClass
    public static class EventType {
        public static final String FOCUS_LOST = "FOCUS_LOST";
        public static final String FOCUS_BACK = "FOCUS_BACK";
        public static final String PAGE_RELOAD = "PAGE_RELOAD";
        /** Ghi bởi server. */
        public static final String STARTED = "STARTED";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String AUTO_SUBMITTED = "AUTO_SUBMITTED";
        public static final String PATTERN = "FOCUS_LOST|FOCUS_BACK|PAGE_RELOAD";
    }

    @UtilityClass
    public static class Flag {
        public static final String IP_CHANGED = "IP_CHANGED";
        public static final String UA_CHANGED = "UA_CHANGED";
    }
}
