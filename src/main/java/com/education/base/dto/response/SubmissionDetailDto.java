package com.education.base.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chi tiết bài nộp cho ngăn chấm bài (staff) - {@code GET /api/v1/lms/assignments/submissions/{sid}}.
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDetailDto {

    private Long id;
    private Long assignmentId;
    private String assignmentTitle;
    private Long classId;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private String status;
    private String contentText;
    private Integer attemptCount;
    private LocalDateTime firstSubmittedAt;
    private LocalDateTime submittedAt;
    private Boolean late;
    private BigDecimal score;
    private BigDecimal maxScore;
    private String feedback;
    private Long gradedById;
    private String gradedByName;
    private LocalDateTime gradedAt;
    private LocalDateTime returnedAt;
    private String returnReason;
    /** Gửi lại làm expectedVersion khi chấm / trả bài. */
    private Long version;
    /** File bài làm (module SUBMISSION); viewUrl/downloadUrl = /api/v1/files/{id}/view|download. */
    private List<FileResponseDto> files;
    /** File nhận xét của giáo viên (module FEEDBACK). */
    private List<FileResponseDto> feedbackFiles;
    /** Mới nhất trước. */
    private List<SubmissionHistoryDto> history;
    private Boolean canGrade;
}
