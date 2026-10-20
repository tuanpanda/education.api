package com.education.base.common.quiz;

import lombok.experimental.UtilityClass;

/**
 * Mã lỗi GĐ4 ({@code docs/contracts/quiz-api.md} §7). HTTP theo {@code GlobalExceptionHandler}:
 * {@code *_NOT_FOUND} -> 404, {@code ForbiddenException} -> 403, {@code RATE_LIMITED} -> 429 + Retry-After, còn lại 400.
 * Mã dùng lại của GĐ2 ({@code GRADE_SCORE_OUT_OF_RANGE}, {@code GRADE_CONFLICT}, {@code GRADE_SYNCED_READONLY},
 * {@code NOT_CLASS_TEACHER}) lấy từ {@code HomeworkErrorCodes}.
 */
@UtilityClass
public class QuizErrorCodes {
    public static final String QUESTION_NOT_FOUND = "QUESTION_NOT_FOUND";
    /** 403. GV sửa/xoá câu không phải của mình (§9 #3). */
    public static final String QUESTION_NOT_OWNER = "QUESTION_NOT_OWNER";
    public static final String QUESTION_INVALID_OPTIONS = "QUESTION_INVALID_OPTIONS";
    /** 400. Lỗi nội dung file nhập (> 500 dòng, sai mẫu, lỗi dòng). Lỗi cấp file dùng FILE_*. */
    public static final String QUESTION_IMPORT_INVALID = "QUESTION_IMPORT_INVALID";
    public static final String QUIZ_NOT_FOUND = "QUIZ_NOT_FOUND";
    public static final String QUIZ_INVALID_SCHEDULE = "QUIZ_INVALID_SCHEDULE";
    public static final String QUIZ_RULE_NOT_ENOUGH_QUESTIONS = "QUIZ_RULE_NOT_ENOUGH_QUESTIONS";
    public static final String QUIZ_LOCKED = "QUIZ_LOCKED";
    public static final String QUIZ_NO_ATTEMPTS_LEFT = "QUIZ_NO_ATTEMPTS_LEFT";
    public static final String QUIZ_ATTEMPT_NOT_FOUND = "QUIZ_ATTEMPT_NOT_FOUND";
    public static final String QUIZ_ATTEMPT_EXPIRED = "QUIZ_ATTEMPT_EXPIRED";
    public static final String QUIZ_ATTEMPT_SUBMITTED = "QUIZ_ATTEMPT_SUBMITTED";
    /** 429 + header Retry-After (§9 #1). */
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String QUIZ_GRADEBOOK_SYNC_FAILED = "QUIZ_GRADEBOOK_SYNC_FAILED";
}
