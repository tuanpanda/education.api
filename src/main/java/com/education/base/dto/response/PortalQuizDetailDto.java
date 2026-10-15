package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - cổng: chi tiết đề. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalQuizDetailDto {

    private Long id;
    private String title;
    private String className;
    private String subjectName;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private Integer durationMin;
    private Integer questionCount;
    private Integer maxAttempts;
    private Integer attemptsUsed;
    private String tab;
    private Long inProgressAttemptId;
    private Boolean canStart;
    private Boolean scoreReleased;
    private BigDecimal finalScore10;
    private String description;
    private String resultMode;
    /** Giờ server ISO-8601 kèm offset (+07:00). */
    private LocalDateTime serverNow;
    private List<AttemptItem> attempts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptItem {
        private Long attemptId;
        private Integer attemptNo;
        private String status;
        private LocalDateTime submittedAt;
        private Boolean scoreReleased;
        private BigDecimal score10;
    }
}
