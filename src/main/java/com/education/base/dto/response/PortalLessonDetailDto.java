package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/v1/portal/me/lessons/{id}?classId=}. KHÔNG tự ghi tiến độ (UI gọi POST progress IN_PROGRESS khi mở).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLessonDetailDto {

    private Long id;

    /** Lớp ngữ cảnh (query hoặc lớp ENROLLED đầu tiên thấy bài). */
    private Long classId;

    private String className;

    private String sectionTitle;

    private String title;

    /** HTML đã sanitize. */
    private String content;

    private Integer estimatedMinutes;

    private LocalDateTime availableFrom;

    private LocalDate sessionDate;

    private List<PortalLessonResourceDto> resources;

    private PortalLessonProgressDto progress;

    private Long prevLessonId;

    private Long nextLessonId;

    private LocalDateTime serverNow;
}
