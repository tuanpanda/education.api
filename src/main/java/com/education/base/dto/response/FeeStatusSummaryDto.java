package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Số khoản phí và số tiền theo trạng thái hiệu lực ({@code O_STATUS_CURSOR} của {@code PRC_RPT_FINANCE_SUMMARY}).
 * <p>
 * Khoản {@code UNPAID} / {@code PARTIAL} đã quá hạn được tính là {@code OVERDUE}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeStatusSummaryDto {

    /** {@code UNPAID}, {@code PARTIAL}, {@code OVERDUE}, {@code PAID}, {@code CANCELLED}. */
    private String status;

    private Long feeCount;

    /** Tổng {@code TOTAL_AMOUNT - DISCOUNT_AMOUNT}. */
    private BigDecimal netAmount;

    /** Còn phải thu (0 với khoản đã thu đủ / đã hủy). */
    private BigDecimal remainingAmount;
}
