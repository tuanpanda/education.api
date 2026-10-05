package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Khoản học phí trên danh sách cổng. Không có NOTE / CANCEL_REASON / TEACHER_COMMENT nội bộ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalFeeListItemDto {

    private Long id;

    private String feeCode;

    private Long classId;

    private String classCode;

    private String className;

    private Integer feeMonth;

    private Integer feeYear;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal paidAmount;

    private BigDecimal remainingAmount;

    private LocalDate dueDate;

    /** {@code UNPAID}, {@code PARTIAL}, {@code PAID}, {@code OVERDUE}, {@code CANCELLED}. */
    private String status;
}
