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
 * {@code POST /api/v1/lms/lessons/{id}/resources} (VIDEO / LINK / ASSIGNMENT) và {@code PUT /api/v1/lms/lesson-resources/{rid}}
 * (không đổi resourceType; FILE chỉ đổi title). FILE tạo bằng multipart {@code /resources/file}.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonResourceRequest {

    @NotBlank
    @Pattern(regexp = LessonConstants.ResourceType.PATTERN)
    private String resourceType;

    /** Rỗng: VIDEO/LINK = host của URL, ASSIGNMENT = tiêu đề bài tập. */
    @Size(max = LessonConstants.TITLE_MAX)
    private String title;

    /** VIDEO / LINK: http(s) tuyệt đối. */
    @Size(max = LessonConstants.URL_MAX)
    private String url;

    /** ASSIGNMENT: bài tập GĐ2 cùng lớp, chưa xoá. */
    private Long assignmentId;
}
