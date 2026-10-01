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

    /**
     * Còn phải thu (B7, V14_3): {@code SUM(TOTAL_AMOUNT - DISCOUNT_AMOUNT - PAID_AMOUNT)} của các khoản đang mở
     * ({@code UNPAID} / {@code PARTIAL} / {@code OVERDUE}) có hạn thu ({@code DUE_DATE}, không có thì ngày lập)
     * trong khoảng lọc. Trước V14_3 là tổng đã lập mọi thời điểm - nay là {@link #totalBilled}.
     */
    private BigDecimal totalReceivable;

    /**
     * Tổng đã lập sau miễn giảm ({@code SUM(TOTAL_AMOUNT - DISCOUNT_AMOUNT)}) của các khoản chưa hủy có hạn thu
     * trong khoảng lọc. Cột mới của V14_3; {@code null} nếu Database chưa chạy V14_3.
     */
    private BigDecimal totalBilled;

    /** Tổng thực thu trong khoảng lọc, chỉ tính giao dịch {@code SUCCESS}. */
    private BigDecimal totalCollected;

    /**
     * Số khoản học phí quá hạn (B7, V14_3): {@code STATUS = 'OVERDUE'} hoặc {@code UNPAID} / {@code PARTIAL} có
     * {@code DUE_DATE} trước hôm nay; không tính khoản đã xóa / đã hủy. Không phụ thuộc khoảng lọc.
     */
    private Long overdueFees;

    /** Còn phải thu của các khoản quá hạn (cùng định nghĩa {@link #overdueFees}). Cột mới của V14_3. */
    private BigDecimal overdueAmount;

    @Builder.Default
    private List<RevenueByMonthDto> revenueByMonth = new ArrayList<>();
}
