package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Giao dịch thanh toán trên cổng. Ẩn {@code voidedAt}/{@code voidedBy}/{@code voidReason} nội bộ;
 * trạng thái {@code VOIDED} vẫn hiển thị.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalPaymentDto {

    private Long id;

    private String transactionCode;

    private Long tuitionFeeId;

    private String feeCode;

    private Long classId;

    private String classCode;

    private String className;

    private BigDecimal amount;

    /** {@code CASH}, {@code BANK_TRANSFER}, {@code VIETQR}, {@code CARD}, {@code EWALLET}. */
    private String paymentMethod;

    private LocalDateTime paymentDate;

    /** {@code PENDING}, {@code SUCCESS}, {@code FAILED}, {@code REFUNDED}, {@code VOIDED}. */
    private String status;

    private String receiptNo;

    /** {@code PAYMENT} hoặc {@code REFUND}. */
    private String transactionType;

    private String payerName;

    private Long refTransactionId;
}
