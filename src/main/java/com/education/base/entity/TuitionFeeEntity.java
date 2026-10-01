package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity ánh xạ bảng {@code FIN_TUITION_FEES} - khoản học phí của học sinh.
 */
@Entity
@Table(name = "FIN_TUITION_FEES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuitionFeeEntity {

    @Id
    @SequenceGenerator(name = "seq_tuition_fee", sequenceName = "SEQ_FIN_TUITION_FEES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tuition_fee")
    @Column(name = "ID")
    private Long id;

    @Column(name = "FEE_CODE", nullable = false, unique = true, length = 30)
    private String feeCode;

    @Column(name = "STUDENT_ID", nullable = false)
    private Long studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STUDENT_ID", insertable = false, updatable = false)
    private StudentEntity student;

    @Column(name = "CLASS_ID")
    private Long classId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLASS_ID", insertable = false, updatable = false)
    private ClassEntity clazz;

    @Column(name = "TOTAL_AMOUNT", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "DISCOUNT_AMOUNT", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "PAID_AMOUNT", nullable = false)
    private BigDecimal paidAmount;

    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "NOTE", length = 255)
    private String note;

    @Column(name = "FEE_MONTH")
    private Integer feeMonth;

    @Column(name = "FEE_YEAR")
    private Integer feeYear;

    @Column(name = "PRICE_PER_SESSION")
    private BigDecimal pricePerSession;

    @Column(name = "TOTAL_SESSIONS")
    private Integer totalSessions;

    @Column(name = "TEACHER_COMMENT", length = 1000)
    private String teacherComment;

    @Column(name = "FOOTER_WISH", length = 500)
    private String footerWish;

    @Column(name = "SLIP_LABEL", length = 50)
    private String slipLabel;

    /** Lý do hủy khoản phí ({@code STATUS = CANCELLED}, V14_1); {@code auto: ...} khi hệ thống tự hủy. */
    @Column(name = "CANCEL_REASON", length = 255)
    private String cancelReason;

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

    @OneToMany(mappedBy = "tuitionFee", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PaymentTransactionEntity> transactions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
        if (status == null || status.isBlank()) {
            status = "UNPAID";
        }
        if (slipLabel == null || slipLabel.isBlank()) {
            slipLabel = "Mặc Định";
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
