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
     * Số thông báo chưa đọc. Phase 1 Stream A trả {@code 0} khi chưa có bảng
     * {@code EDU_ANNOUNCEMENTS} — Stream B sẽ triển khai {@code PortalAnnouncementQuery}.
     */
    @Builder.Default
    private long unreadAnnouncements = 0L;
}
