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

    /** {@code FEE} (ghi nợ) hoặc {@code PAYMENT} (ghi có). */
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

    private LocalDate dueDate;

    private String classCode;

    private String className;

    private String paymentMethod;

    private String status;

    private String note;

    private BigDecimal debitAmount;

    private BigDecimal creditAmount;

    /** Số dư lũy kế sau dòng này. */
    private BigDecimal balance;
}
