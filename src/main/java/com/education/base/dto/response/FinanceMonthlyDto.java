package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Một tháng trong chuỗi tổng hợp tài chính ({@code O_MONTHLY_CURSOR} của {@code PRC_RPT_FINANCE_SUMMARY}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceMonthlyDto {

    /** Tháng dạng {@code yyyy-MM}. */
    private String month;

    /** Phải thu sau miễn giảm của các khoản có kỳ trong tháng. */
    private BigDecimal billedAmount;

    private Long feeCount;

    /** Thực thu sau hoàn tiền trong tháng (thu {@code SUCCESS}/{@code REFUNDED} − hoàn {@code SUCCESS}); có thể âm. */
    private BigDecimal collectedAmount;

    /** Tiền đã hoàn trong tháng (đã trừ trong {@link #collectedAmount}). */
    private BigDecimal refundedAmount;

    private Long transactionCount;
}
