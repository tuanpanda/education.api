package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - ma trận HS × lượt làm của 1 đề. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptMatrixDto {

    private QuizListItemDto quiz;
    private List<Row> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Long studentId;
        private String studentCode;
        private String fullName;
        private String enrollmentStatus;
        private Integer attemptsUsed;
        private BigDecimal bestScore;
        private BigDecimal finalScore;
        private BigDecimal finalScore10;
        private String status;
        private Long lastAttemptId;
        private Integer focusLostCount;
        /** Lượt cuối SUBMITTED/AUTO_SUBMITTED còn câu tự luận chưa chấm. */
        private Boolean pendingEssay;
        private List<String> flags;
    }
}
