package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một khoản phí còn nợ trong báo cáo tuổi nợ ({@code O_DATA_CURSOR} của {@code PRC_RPT_DEBT_AGING}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtAgingFeeDto {

    private Long feeId;

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

    private LocalDate dueDate;

    private String status;

    private BigDecimal netAmount;

    private BigDecimal paidAmount;

    private BigDecimal remainingAmount;

    /** Số ngày quá hạn tại ngày chốt (0 nếu chưa tới hạn). */
    private Integer daysPastDue;

    /** Một trong {@code DomainConstants.DEBT_AGING_BUCKETS}. */
    private String agingBucket;
}
