package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - dòng danh sách đề. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizListItemDto {

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
}
