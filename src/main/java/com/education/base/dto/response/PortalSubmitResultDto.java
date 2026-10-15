package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - cổng: kết quả nộp (không có đáp án). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalSubmitResultDto {

    private Long attemptId;
    private String status;
    private LocalDateTime submittedAt;
    /** Giờ server ISO-8601 kèm offset (+07:00). */
    private LocalDateTime serverNow;
    private Long remainingSeconds;
    private Boolean scoreReleased;
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal score10;
    private Boolean pendingEssay;
}
