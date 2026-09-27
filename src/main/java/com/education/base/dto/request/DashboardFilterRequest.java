package com.education.base.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Khoảng thời gian thống kê cho {@code PRC_RPT_DASHBOARD_METRICS}.
 * <p>
 * Bỏ trống cả hai trường thì Procedure tự lấy 12 tháng gần nhất. Điều kiện
 * {@code fromDate <= toDate} được Procedure kiểm tra và trả về mã lỗi
 * {@code INVALID_DATE_RANGE} nếu vi phạm.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardFilterRequest {

    private LocalDate fromDate;

    private LocalDate toDate;
}
