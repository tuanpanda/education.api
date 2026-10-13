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

import java.util.List;

/**
 * {@code PUT /api/v1/lms/lessons/reorder}: thứ tự MỚI của toàn bộ chương + bài của MỘT chủ (khoá hoặc lớp).
 * Phải liệt kê đủ mọi chương/bài chưa xoá của chủ đó (thiếu/thừa/khác chủ -> 400 LESSON_REORDER_INVALID).
 * Bài được phép chuyển chương (cùng chủ). SORT_ORDER = vị trí trong danh sách (0-based).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonReorderRequest {

    @NotBlank
    @Pattern(regexp = LessonConstants.OwnerType.PATTERN)
    private String ownerType;

    @NotNull
    private Long ownerId;

    @NotNull
    @Valid
    private List<SectionOrder> sections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionOrder {

        @NotNull
        private Long sectionId;

        @NotNull
        private List<Long> lessonIds;
    }
}
