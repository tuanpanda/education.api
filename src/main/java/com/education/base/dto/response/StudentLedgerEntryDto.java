package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng của sổ công nợ học sinh ({@code O_DATA_CURSOR} của {@code PRC_RPT_STUDENT_LEDGER}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLedgerEntryDto {

    /** {@code FEE} (ghi nợ), {@code PAYMENT} (ghi có) hoặc {@code REFUND} (hoàn tiền, ghi nợ). */
    private String entryType;

    /** Ngày lập khoản phí / ngày thanh toán. */
    private LocalDateTime entryDate;

    /** ID khoản phí hoặc giao dịch. */
    private Long refId;

    /** Mã khoản phí hoặc mã giao dịch. */
    private String refCode;

    private Long feeId;

    private String feeCode;

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

    private String classCode;

    private String className;

    private String paymentMethod;

    /** Số phiếu thu / phiếu chi (dòng {@code PAYMENT} / {@code REFUND}). */
    private String receiptNo;

    private String status;

    private String note;

    private BigDecimal debitAmount;

    private BigDecimal creditAmount;

    /** Số dư lũy kế sau dòng này. */
    private BigDecimal balance;
}
