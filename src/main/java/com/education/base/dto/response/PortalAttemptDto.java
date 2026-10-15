package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - cổng: lượt đang làm. KHÔNG BAO GIỜ có isCorrect/đáp án/giải thích. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalAttemptDto {

    private Long attemptId;
    private Long quizId;
    private String title;
    private Integer attemptNo;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    /** Giờ server ISO-8601 kèm offset (+07:00). */
    private LocalDateTime serverNow;
    private Long remainingSeconds;
    private List<Question> questions;
    private List<SavedAnswer> savedAnswers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Question {
        private Long questionId;
        private Integer index;
        private String type;
        private String content;
        private BigDecimal points;
        private List<Option> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private Long id;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavedAnswer {
        private Long questionId;
        private List<Long> selectedOptionIds;
        private String textAnswer;
        private LocalDateTime savedAt;
    }
}
