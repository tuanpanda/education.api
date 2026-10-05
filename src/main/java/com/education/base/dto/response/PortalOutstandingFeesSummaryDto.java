package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Tóm tắt học phí còn nợ trên dashboard cổng.
 * Chỉ tính khoản {@code UNPAID} / {@code PARTIAL} / {@code OVERDUE} (không gồm PAID / CANCELLED).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalOutstandingFeesSummaryDto {

    /** Số khoản còn phải thu. */
    private long outstandingCount;

    /** Tổng số tiền còn phải thu. */
    @Builder.Default
    private BigDecimal totalRemaining = BigDecimal.ZERO;
}
