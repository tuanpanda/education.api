package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng của file Excel danh sách giao dịch ({@code /api/v1/reports/export/payment-transactions}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionExportRowDto {

    private Long id;

    private String transactionCode;

    private Long tuitionFeeId;

    private String feeCode;

    private Long studentId;

    private String studentCode;

    private String studentName;

    private Long classId;

    private String classCode;

    private String className;

    private BigDecimal amount;

    private String paymentMethod;

    private LocalDateTime paymentDate;

    private String bankBin;

    private String accountNo;

    private String bankReferenceNo;

    private String status;

    private String note;

    private String createdBy;

    // ---- V14_2: phiếu thu, loại giao dịch, hủy / hoàn tiền -----------------------------------

    /** Số phiếu thu / phiếu chi. */
    private String receiptNo;

    /** {@code PAYMENT} (thu) hoặc {@code REFUND} (hoàn, số dương). */
    private String transactionType;

    /** Người nộp (thu) / người nhận (hoàn). */
    private String payerName;

    /** Dòng {@code REFUND}: mã giao dịch thu gốc. */
    private String refTransactionCode;

    private LocalDateTime voidedAt;

    private String voidReason;

    /**
     * Tác động lên thực thu: {@code +amount} với thu {@code SUCCESS}/{@code REFUNDED}, {@code -amount} với hoàn
     * {@code SUCCESS}, {@code 0} với {@code VOIDED}/{@code PENDING}/{@code FAILED} (cùng quy tắc {@code PAID_AMOUNT}).
     */
    public BigDecimal getNetAmount() {
        if (amount == null) {
            return null;
        }
        if ("REFUND".equals(transactionType)) {
            return "SUCCESS".equals(status) ? amount.negate() : BigDecimal.ZERO;
        }
        return "SUCCESS".equals(status) || "REFUNDED".equals(status) ? amount : BigDecimal.ZERO;
    }
}
