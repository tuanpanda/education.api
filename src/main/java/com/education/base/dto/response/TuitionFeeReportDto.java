package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một dòng trong danh sách khoản học phí.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuitionFeeReportDto {

    private Long id;
    private String feeCode;

    private Long studentId;
    private String studentCode;
    private String studentName;

    private Long classId;
    private String classCode;
    private String className;

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal paidAmount;

    /** Số tiền còn phải thu: {@code totalAmount - discountAmount - paidAmount}, do Database tính. */
    private BigDecimal remainingAmount;

    private LocalDate dueDate;

    /** {@code UNPAID}, {@code PARTIAL}, {@code PAID}, {@code OVERDUE}, {@code CANCELLED}. */
    private String status;
}
