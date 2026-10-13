package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/v1/portal/me/classes/{classId}/lessons}.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLessonTreeDto {

    private Long classId;

    private String className;

    private Integer totalLessons;

    private Integer completedLessons;

    private Integer percent;

    private List<PortalLessonSectionDto> sections;

    private LocalDateTime serverNow;
}
