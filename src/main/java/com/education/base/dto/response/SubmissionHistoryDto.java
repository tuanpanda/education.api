package com.education.base.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một lần nộp trong lịch sử (staff + portal dùng chung, không chứa dữ liệu nội bộ).
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionHistoryDto {

    private Integer attemptNo;
    private String contentSnapshot;
    private List<Long> fileIds;
    private LocalDateTime submittedAt;
    private Boolean late;
    /** SUBMITTED / RETURNED / GRADED. */
    private String outcome;
    private BigDecimal score;
    private String feedback;
    private LocalDateTime actedAt;
}
