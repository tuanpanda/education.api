package com.education.base.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/v1/portal/me/assignments/summary} - huy hiệu trang chủ cổng.
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalHomeworkSummaryDto {

    /** Số bài tab todo. */
    private int todoCount;
    /** Trong todo: hạn trong {@code HomeworkConstants.DUE_SOON_DAYS} ngày tới. */
    private int dueSoonCount;
    private int overdueCount;
    /** Hạn gần nhất trong todo, NULL nếu không có. */
    private LocalDateTime nextDueAt;
}
