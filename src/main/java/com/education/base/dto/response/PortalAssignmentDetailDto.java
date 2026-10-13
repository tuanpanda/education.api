package com.education.base.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/v1/portal/me/assignments/{id}}: đề + bài nộp của tôi.
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalAssignmentDetailDto {

    private Long id;
    private Long classId;
    private String className;
    private LocalDate sessionDate;
    private String title;
    private String description;
    private String submissionType;
    private BigDecimal maxScore;
    private LocalDateTime dueAt;
    private Boolean allowLate;
    private LocalDateTime lateUntil;
    private Integer maxFiles;
    private Boolean allowResubmit;
    private String assignmentStatus;
    private String tab;
    private Boolean overdue;
    /** Còn sửa nháp / thêm-xoá file được. */
    private Boolean canEdit;
    /** Bấm Nộp được ngay (nội dung hợp lệ chưa xét). */
    private Boolean canSubmit;
    /** Nộp lúc này sẽ bị đánh dấu muộn. */
    private Boolean willBeLate;
    /** Giờ server lúc trả (Asia/Ho_Chi_Minh) - UI tính đếm ngược, không tin đồng hồ máy. */
    private LocalDateTime serverNow;
    private List<PortalFileDto> attachments;
    /** NULL khi chưa có. */
    private PortalSubmissionDto mySubmission;
}
