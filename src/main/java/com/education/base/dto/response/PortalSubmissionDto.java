package com.education.base.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bài nộp của học sinh đang đăng nhập (không có dữ liệu người chấm nội bộ).
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalSubmissionDto {

    private Long id;
    /** DRAFT / SUBMITTED / RETURNED / GRADED. */
    private String status;
    private String contentText;
    private Integer attemptCount;
    private LocalDateTime submittedAt;
    private Boolean late;
    /** Chỉ khi GRADED. */
    private BigDecimal score;
    /** Chỉ khi GRADED hoặc RETURNED. */
    private String feedback;
    private String returnReason;
    private LocalDateTime gradedAt;
    private List<PortalFileDto> files;
    private List<PortalFileDto> feedbackFiles;
    /** Mới nhất trước. */
    private List<SubmissionHistoryDto> history;
}
