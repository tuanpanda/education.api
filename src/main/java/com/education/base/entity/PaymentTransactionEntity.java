package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code FIN_PAYMENT_TRANSACTIONS} - giao dịch thanh toán học phí.
 */
@Entity
@Table(name = "FIN_PAYMENT_TRANSACTIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionEntity {

    @Id
    @SequenceGenerator(name = "seq_payment_trans", sequenceName = "SEQ_FIN_PAYMENT_TRANS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_payment_trans")
    @Column(name = "ID")
    private Long id;

    @Column(name = "TRANSACTION_CODE", nullable = false, unique = true, length = 50)
    private String transactionCode;

    @Column(name = "TUITION_FEE_ID", nullable = false)
    private Long tuitionFeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TUITION_FEE_ID", insertable = false, updatable = false)
    private TuitionFeeEntity tuitionFee;

    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;

    @Column(name = "PAYMENT_METHOD", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "PAYMENT_DATE", nullable = false)
    private LocalDateTime paymentDate;

    @Column(name = "BANK_BIN", length = 20)
    private String bankBin;

    @Column(name = "ACCOUNT_NO", length = 30)
    private String accountNo;

    @Column(name = "BANK_REFERENCE_NO", length = 100)
    private String bankReferenceNo;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "NOTE", length = 255)
    private String note;

    /** Số phiếu thu / phiếu chi (V14_2), sinh từ {@code SYS_CODE_RULES 'RECEIPT'}; duy nhất khi khác null. */
    @Column(name = "RECEIPT_NO", length = 30)
    private String receiptNo;

    /** {@code PAYMENT} (thu) hoặc {@code REFUND} (hoàn tiền, số dương) - {@code CK_TRANS_TYPE} (V14_2). */
    @Column(name = "TRANSACTION_TYPE", nullable = false, length = 20)
    private String transactionType;

    /** Người nộp tiền (thu) / người nhận tiền (hoàn). */
    @Column(name = "PAYER_NAME", length = 150)
    private String payerName;

    @Column(name = "VOIDED_AT")
    private LocalDateTime voidedAt;

    @Column(name = "VOIDED_BY", length = 50)
    private String voidedBy;

    @Column(name = "VOID_REASON", length = 255)
    private String voidReason;

    /** Giao dịch thu gốc của một dòng hoàn tiền ({@code FK_TRANS_REF_TRANS}, V14_2). */
    @Column(name = "REF_TRANSACTION_ID")
    private Long refTransactionId;

    @Column(name = "IS_DELETED", nullable = false)
    private Integer isDeleted;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
        if (transactionType == null || transactionType.isBlank()) {
            transactionType = "PAYMENT";
        }
        if (paymentDate == null) {
            paymentDate = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
