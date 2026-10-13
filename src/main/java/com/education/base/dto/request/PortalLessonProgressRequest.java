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
 * {@code POST /api/v1/portal/me/lessons/{id}/progress}. IN_PROGRESS khi mở bài (tăng VIEW_COUNT), COMPLETED khi bấm
 * "Đánh dấu đã học". COMPLETED không lùi về IN_PROGRESS.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLessonProgressRequest {

    @NotBlank
    @Pattern(regexp = LessonConstants.ProgressStatus.REQUEST_PATTERN)
    private String status;
}
