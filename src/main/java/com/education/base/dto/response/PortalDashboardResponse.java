package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard cổng học sinh ({@code GET /api/v1/portal/me/dashboard}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalDashboardResponse {

    /** Buổi học sắp tới gần nhất; {@code null} nếu không còn buổi SCHEDULED. */
    private PortalNextSessionDto nextSession;

    @Builder.Default
    private PortalOutstandingFeesSummaryDto outstandingFees = PortalOutstandingFeesSummaryDto.builder().build();

    /**
     * Số thông báo PUBLISHED còn hiệu lực mà học sinh chưa đọc
     * ({@code AnnouncementQueryService.countUnread}, theo SYS_USERS.ID của phiên).
     */
    @Builder.Default
    private long unreadAnnouncements = 0L;
}
