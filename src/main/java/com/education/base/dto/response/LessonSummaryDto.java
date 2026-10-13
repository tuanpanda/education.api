package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một bài trong cây chương/bài phía nhân viên.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonSummaryDto {

    private Long id;

    private Long sectionId;

    private String title;

    private String status;

    private Integer sortOrder;

    /** Giá trị đã lưu. */
    private LocalDateTime availableFrom;

    /** Giờ mở thực tế (availableFrom, hoặc ngày+giờ bắt đầu buổi học, hoặc publishedAt). */
    private LocalDateTime effectiveAvailableFrom;

    private Long sessionId;

    private LocalDate sessionDate;

    private Integer estimatedMinutes;

    private Integer resourceCount;

    /** PUBLISHED và effectiveAvailableFrom <= now (giờ server). */
    private Boolean availableNow;
}
