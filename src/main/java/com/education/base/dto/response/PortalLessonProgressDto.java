package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tiến độ của học sinh với một bài (+ tổng của lớp ngữ cảnh).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLessonProgressDto {

    private Long lessonId;

    private String status;

    private LocalDateTime firstViewedAt;

    private LocalDateTime lastViewedAt;

    private LocalDateTime completedAt;

    private Integer viewCount;

    private Long classId;

    private Integer classCompleted;

    private Integer classTotal;

    private Integer classPercent;
}
