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
 * {@code POST /api/v1/lms/lesson-sections} (tạo: cần ownerType + ownerId) và {@code PUT /{id}} (chỉ đổi title).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonSectionUpsertRequest {

    /** COURSE / CLASS. Bỏ qua khi PUT. */
    @Pattern(regexp = LessonConstants.OwnerType.PATTERN)
    private String ownerType;

    /** courseId hoặc classId. Bỏ qua khi PUT. */
    private Long ownerId;

    @NotBlank
    @Size(max = LessonConstants.TITLE_MAX)
    private String title;
}
