package com.education.base.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code POST /api/v1/lms/assignments} và {@code PUT /api/v1/lms/assignments/{id}}. Thời điểm ISO
 * {@code yyyy-MM-ddTHH:mm:ss} không múi giờ = giờ Asia/Ho_Chi_Minh. Sau khi PUBLISHED không đổi được {@code classId}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentUpsertRequest {

    @NotNull(message = "Vui lòng chọn lớp")
    private Long classId;

    /** Buổi học của lớp (tuỳ chọn). */
    private Long sessionId;

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    private String title;

    /** HTML CKEditor; backend sanitize. */
    private String description;

    @NotBlank(message = "Vui lòng chọn kiểu nộp bài")
    @Pattern(regexp = "TEXT|FILE|BOTH", message = "Kiểu nộp bài phải là TEXT, FILE hoặc BOTH")
    private String submissionType;

    @NotNull(message = "Thang điểm không được để trống")
    @DecimalMin(value = "0.01", message = "Thang điểm phải lớn hơn 0")
    @DecimalMax(value = "100", message = "Thang điểm tối đa 100")
    private BigDecimal maxScore;

    @NotNull(message = "Vui lòng nhập hạn nộp")
    private LocalDateTime dueAt;

    private Boolean allowLate;

    /** Chỉ khi {@code allowLate = true}; phải sau {@code dueAt}. NULL = nộp muộn tới khi đóng bài. */
    private LocalDateTime lateUntil;

    /** 0..10; bài TEXT dùng 0. Không vượt MAX_FILES_PER_REF của module SUBMISSION. */
    @Min(value = 0, message = "Số file tối đa không hợp lệ")
    @Max(value = 10, message = "Số file tối đa là 10")
    private Integer maxFiles;

    private Boolean allowResubmit;

    /** Đẩy điểm vào sổ điểm EDU_GRADES (GRADE_TYPE=ASSIGNMENT, quy về thang 10). */
    private Boolean syncToGradebook;

    @DecimalMin(value = "0.01", message = "Hệ số phải lớn hơn 0")
    private BigDecimal gradeWeight;
}
