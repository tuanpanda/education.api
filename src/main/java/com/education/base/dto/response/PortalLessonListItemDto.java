package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bài trong cây cổng học sinh (chỉ bài PUBLISHED đã tới giờ mở).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLessonListItemDto {

    private Long id;

    private String title;

    private Integer estimatedMinutes;

    /** Giờ mở thực tế. */
    private LocalDateTime availableFrom;

    private LocalDate sessionDate;

    /** NOT_STARTED / IN_PROGRESS / COMPLETED. */
    private String progressStatus;

    private LocalDateTime completedAt;

    private Boolean hasVideo;

    private Integer resourceCount;
}
