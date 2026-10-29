package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng của file Excel danh sách khoản học phí ({@code /api/v1/reports/export/tuition-fees}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeExportRowDto {

    private Long id;

    private String feeCode;

    private Long studentId;

    private String studentCode;

    private String studentName;

    /** Trạng thái học sinh; {@code DELETED} nếu đã xóa mềm. */
    private String studentStatus;

    private Long classId;

    private String classCode;

    private String className;

    private Integer feeYear;

    private Integer feeMonth;

    /** Kỳ thu theo khoảng ngày ({@code null} với phiếu cũ chưa backfill / khoản tạo tay). */
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

    private String status;

    private String note;

    private LocalDateTime createdAt;
}
