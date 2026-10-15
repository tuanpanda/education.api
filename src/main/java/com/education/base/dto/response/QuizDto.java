package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - chi tiết đề (nhân viên). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizDto {

    private Long id;
    private Long classId;
    private String className;
    private String title;
    private String status;
    private Boolean isOpen;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private Integer durationMin;
    private BigDecimal maxScore;
    private Integer questionCount;
    private Stats stats;
    private Long lessonId;
    private String description;
    private Integer maxAttempts;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private String resultMode;
    private String scorePolicy;
    private BigDecimal passScore;
    private Boolean syncToGradebook;
    private LocalDateTime resultsReleasedAt;
    private List<QuestionItem> questions;
    private List<RuleItem> rules;
    private Boolean hasAttempts;
    private Boolean canEdit;
    private Long version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private Long enrolled;
        private Long started;
        private Long submitted;
        private Long pendingEssay;
        private BigDecimal avgScore10;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionItem {
        private Long questionId;
        private BigDecimal points;
        private Integer sortOrder;
        private String type;
        private String contentPreview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItem {
        private String topic;
        private Integer difficulty;
        private Integer questionCount;
        private BigDecimal pointsEach;
        private Integer candidateCount;
    }
}
