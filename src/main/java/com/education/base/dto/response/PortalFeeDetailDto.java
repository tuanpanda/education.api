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
 * Chi tiết khoản học phí trên cổng + lịch sử thanh toán (đã ẩn chi tiết VOID nội bộ).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalFeeDetailDto {

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

    private String status;

    @Builder.Default
    private List<PortalPaymentDto> payments = new ArrayList<>();
}
