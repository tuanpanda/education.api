package com.education.base.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một dòng ma trận bài nộp: MỌI học sinh ENROLLED của lớp, kể cả chưa có bài nộp.
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRowDto {

    private Long studentId;
    private String studentCode;
    private String studentName;
    /** NULL khi học sinh chưa mở/lưu bài. */
    private Long submissionId;
    /** HomeworkConstants.MatrixState: NOT_SUBMITTED / MISSING / SUBMITTED / LATE / RETURNED / GRADED. */
    private String state;
    /** LMS_SUBMISSIONS.STATUS hoặc NULL. */
    private String status;
    private Integer attemptCount;
    private LocalDateTime submittedAt;
    private Boolean late;
    private BigDecimal score;
    private LocalDateTime gradedAt;
    private Integer fileCount;
}
