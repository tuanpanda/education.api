package com.education.base.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một bài trong danh sách cổng {@code GET /api/v1/portal/me/assignments?tab=}.
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalAssignmentListItemDto {

    private Long id;
    private Long classId;
    private String className;
    private String title;
    private String submissionType;
    private BigDecimal maxScore;
    private LocalDateTime dueAt;
    private Boolean allowLate;
    private LocalDateTime lateUntil;
    /** PUBLISHED / CLOSED (học sinh không bao giờ thấy DRAFT). */
    private String assignmentStatus;
    /** NULL nếu chưa có bài nộp; còn lại LMS_SUBMISSIONS.STATUS. */
    private String submissionStatus;
    /** todo / submitted / graded / overdue - tab mà bài này thuộc về. */
    private String tab;
    private LocalDateTime submittedAt;
    private Boolean late;
    /** Chỉ khi GRADED. */
    private BigDecimal score;
    /** Đã quá DUE_AT (giờ server). */
    private Boolean overdue;
    /** Còn nộp/nộp lại được ngay lúc này (giờ server). */
    private Boolean canSubmit;
}
