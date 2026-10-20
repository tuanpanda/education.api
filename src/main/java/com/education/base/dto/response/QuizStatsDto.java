package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - thống kê đề. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizStatsDto {

    private Long submittedCount;
    private BigDecimal avgScore10;
    private BigDecimal maxScore10;
    private BigDecimal minScore10;
    private BigDecimal passRate;
    private List<Bucket> distribution;
    private List<QuestionStat> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket {
        private String bucket;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionStat {
        private Long questionId;
        private String contentPreview;
        private String type;
        private BigDecimal correctRate;
        private List<OptionStat> optionStats;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionStat {
        private Long optionId;
        private String content;
        private Boolean isCorrect;
        private Long pickCount;
    }
}
