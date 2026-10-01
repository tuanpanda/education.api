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
 * Tổng hợp tài chính trong khoảng ngày, map từ {@code PRC_RPT_FINANCE_SUMMARY}.
 * <p>
 * Khoản phí thuộc khoảng theo {@code DUE_DATE} (không có thì {@code CREATED_AT}); thực thu theo ngày thanh toán
 * của giao dịch {@code SUCCESS}. Xem định nghĩa chi tiết ở đầu {@code V14_3__fin_reports.sql}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceSummaryDto {

    /** Khoảng thời gian thực tế đã áp dụng, do Procedure trả về. */
    private LocalDate fromDate;

    private LocalDate toDate;

    /** Tổng tiền đã lập (TOTAL_AMOUNT) của các khoản chưa hủy trong kỳ. */
    private BigDecimal totalBilled;

    /** Tổng miễn giảm (DISCOUNT_AMOUNT) của các khoản chưa hủy trong kỳ. */
    private BigDecimal totalDiscount;

    /** Phải thu sau miễn giảm: {@code totalBilled - totalDiscount}. */
    private BigDecimal netBilled;

    /** Thực thu: tổng giao dịch {@code SUCCESS} có ngày thanh toán trong kỳ. */
    private BigDecimal totalCollected;

    /** Số giao dịch {@code SUCCESS} trong kỳ. */
    private Long transactionCount;

    /** Còn phải thu của các khoản đang mở (UNPAID/PARTIAL/OVERDUE) trong kỳ. */
    private BigDecimal totalOutstanding;

    /** Còn phải thu của các khoản quá hạn trong kỳ. */
    private BigDecimal overdueAmount;

    /** Số khoản quá hạn trong kỳ. */
    private Long overdueFees;

    /** Số khoản chưa hủy trong kỳ. */
    private Long feeCount;

    /** Số khoản / số tiền theo trạng thái hiệu lực. */
    @Builder.Default
    private List<FeeStatusSummaryDto> statusBreakdown = new ArrayList<>();

    /** Từng tháng trong kỳ (kể cả tháng không phát sinh), dùng vẽ biểu đồ. */
    @Builder.Default
    private List<FinanceMonthlyDto> monthly = new ArrayList<>();
}
