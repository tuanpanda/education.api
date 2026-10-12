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
 * Bài tập (staff) - {@code /api/v1/lms/assignments}.
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentDto {

    private Long id;
    private Long classId;
    private String classCode;
    private String className;
    private Long sessionId;
    private LocalDate sessionDate;
    /** Ví dụ "12/10/2026 18:00-19:30 - Chủ đề". */
    private String sessionLabel;
    private String title;
    /** HTML đã sanitize. Danh sách (search) trả NULL để nhẹ. */
    private String description;
    private String submissionType;
    private BigDecimal maxScore;
    private LocalDateTime dueAt;
    private Boolean allowLate;
    private LocalDateTime lateUntil;
    private Integer maxFiles;
    private Boolean allowResubmit;
    private Boolean syncToGradebook;
    private BigDecimal gradeWeight;
    /** DRAFT / PUBLISHED / CLOSED. */
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime closedAt;
    /** Optimistic lock - gửi lại không bắt buộc với PUT (Bot 1 có thể bổ sung). */
    private Long version;
    /** File đề (module ASSIGNMENT). Danh sách (search) trả rỗng. */
    private List<FileResponseDto> attachments;
    private AssignmentStatsDto stats;
    /** Được sửa/đăng/đóng (quyền + phạm vi lớp). */
    private Boolean canManage;
    /** Được chấm/trả bài (MENU_HOMEWORK:GRADE + phạm vi lớp). */
    private Boolean canGrade;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
