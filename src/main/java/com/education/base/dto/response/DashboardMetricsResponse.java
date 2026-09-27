package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Chỉ số tổng hợp cho Bảng điều khiển.
 * <p>
 * Map từ hai Ref Cursor của {@code PRC_RPT_DASHBOARD_METRICS}: {@code O_SUMMARY_CURSOR}
 * cho các chỉ số dạng số và {@code O_REVENUE_CURSOR} cho {@link #revenueByMonth}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardMetricsResponse {

    /** Khoảng thời gian thực tế đã áp dụng, do Procedure trả về. */
    private LocalDate fromDate;

    private LocalDate toDate;

    private Long totalStudents;

    private Long activeStudents;

    private Long totalClasses;

    /** Số lớp đang ở trạng thái {@code OPEN} hoặc {@code ONGOING}. */
    private Long activeClasses;

    /** Số lead phát sinh trong khoảng thời gian lọc. */
    private Long newLeads;

    private Long convertedLeads;

    /** Tổng phải thu: {@code SUM(TOTAL_AMOUNT - DISCOUNT_AMOUNT)} của các khoản chưa bị hủy. */
    private BigDecimal totalReceivable;

    /** Tổng thực thu trong khoảng lọc, chỉ tính giao dịch {@code SUCCESS}. */
    private BigDecimal totalCollected;

    /** Số khoản học phí quá hạn mà chưa thu đủ. */
    private Long overdueFees;

    @Builder.Default
    private List<RevenueByMonthDto> revenueByMonth = new ArrayList<>();
}
