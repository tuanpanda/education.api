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

    /** Kỳ thu theo khoảng ngày ({@code null} với phiếu cũ chưa backfill). */
    private java.time.LocalDate periodFrom;

    private java.time.LocalDate periodTo;

    /** {@code "Từ dd/MM/yyyy đến dd/MM/yyyy"} hoặc {@code "Tháng MM/yyyy"} (phiếu cũ). */
    private String periodText;

    /**
     * {@code true} khi phiếu tạo bởi "thu theo khoảng ngày" (DB {@code FEE_MONTH IS NULL AND PERIOD_FROM IS NOT NULL});
     * phiếu tháng cũ (kể cả đã backfill PERIOD_*) = {@code false}. FE dùng để ẩn dòng giảm giá.
     */
    private boolean rangeFee;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal paidAmount;

    private BigDecimal remainingAmount;

    private LocalDate dueDate;

    /** {@code UNPAID}, {@code PARTIAL}, {@code PAID}, {@code OVERDUE}, {@code CANCELLED}. */
    private String status;
}
