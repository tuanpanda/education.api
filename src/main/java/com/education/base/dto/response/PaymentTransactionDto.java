package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một giao dịch thanh toán.
 * <p>
 * Các trường khớp cột {@code O_TRANSACTION_CURSOR} của {@code PRC_GET_TUITION_FEE_DETAIL}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionDto {

    private Long id;
    private String transactionCode;
    private Long tuitionFeeId;
    private BigDecimal amount;

    /** {@code CASH}, {@code BANK_TRANSFER}, {@code VIETQR}, {@code CARD}, {@code EWALLET}. */
    private String paymentMethod;

    private LocalDateTime paymentDate;
    private String bankBin;
    private String accountNo;
    private String bankReferenceNo;

    /** {@code PENDING}, {@code SUCCESS}, {@code FAILED}, {@code REFUNDED}. */
    private String status;

    private String note;
}
