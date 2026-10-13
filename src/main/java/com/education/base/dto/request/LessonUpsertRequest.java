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

import java.time.LocalDateTime;

/**
 * {@code POST/PUT /api/v1/lms/lessons[/{id}]}. Chủ (khoá/lớp) suy ra từ chương; đổi sang chương khác chủ -> 400 LESSON_INVALID.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonUpsertRequest {

    @NotNull
    private Long sectionId;

    @NotBlank
    @Size(max = LessonConstants.TITLE_MAX)
    private String title;

    /** HTML (CKEditor), sanitize ở server, tối đa CONTENT_MAX ký tự. */
    private String content;

    /** Buổi học - chỉ bài thuộc lớp, buổi phải cùng lớp và không CANCELLED. */
    private Long sessionId;

    /** Giờ mở bài (giờ server VN). NULL = theo buổi học nếu có, không thì mở ngay khi đăng. */
    private LocalDateTime availableFrom;

    @Min(1)
    @Max(1440)
    private Integer estimatedMinutes;
}
