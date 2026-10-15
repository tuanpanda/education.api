package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - cổng: 1 đề trong danh sách (không lộ version/IP/người tạo). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalQuizListItemDto {

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
}
