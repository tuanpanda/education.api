package com.education.base.repository;

import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.response.DashboardMetricsResponse;

/**
 * Báo cáo tổng hợp qua Standalone Procedure {@code PRC_RPT_DASHBOARD_METRICS}.
 * Không kế thừa {@code JpaRepository} vì không ánh xạ một bảng cụ thể.
 */
public interface ReportRepository {

    /**
     * Lấy chỉ số bảng điều khiển trong khoảng thời gian lọc.
     * Bỏ trống ngày thì procedure tự lấy 12 tháng gần nhất.
     *
     * @param filter khoảng ngày; {@code null} tương đương không lọc.
     * @return chỉ số tổng hợp kèm doanh thu theo tháng.
     */
    DashboardMetricsResponse getDashboardMetrics(DashboardFilterRequest filter);
}
