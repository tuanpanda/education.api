package com.education.base.dto.request;

import com.education.base.common.lesson.LessonConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code POST/PUT /api/v1/lms/courses[/{id}]}. Mô tả HTML được sanitize ở server.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseUpsertRequest {

    @NotBlank
    @Size(max = LessonConstants.COURSE_CODE_MAX)
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "Mã khoá chỉ gồm chữ, số, dấu _ . -")
    private String courseCode;

    @NotBlank
    @Size(max = LessonConstants.COURSE_NAME_MAX)
    private String courseName;

    @Size(max = 150)
    private String subjectName;

    @Min(1)
    @Max(12)
    private Integer gradeLevel;

    /** HTML (CKEditor). */
    private String description;

    /** NULL khi tạo = ACTIVE. */
    @Pattern(regexp = LessonConstants.CourseStatus.PATTERN)
    private String status;
}
