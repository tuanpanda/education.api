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

    /** Thực thu (giao dịch {@code SUCCESS}) trong tháng. */
    private BigDecimal collectedAmount;

    private Long transactionCount;
}
