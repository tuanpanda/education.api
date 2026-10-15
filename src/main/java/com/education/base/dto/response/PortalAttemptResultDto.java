package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - cổng: kết quả lượt làm; đáp án chỉ khi answersReleased. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalAttemptResultDto {

    private Long attemptId;
    private String status;
    private Boolean scoreReleased;
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal score10;
    private Boolean passed;
    private Boolean pendingEssay;
    private Boolean answersReleased;
    private List<Question> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Question {
        private Long questionId;
        private String content;
        private String type;
        private BigDecimal points;
        private List<Option> options;
        private List<Long> selectedOptionIds;
        private String textAnswer;
        private Boolean isCorrect;
        private BigDecimal pointsAwarded;
        private List<Long> correctOptionIds;
        private List<String> acceptedAnswers;
        private String explanation;
        private String graderComment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private Long id;
        private String content;
    }
}
