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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code FIN_STUDENT_DISCOUNTS} (V14_1) - miễn giảm / học bổng của học sinh.
 * <p>
 * {@code classId = null}: áp dụng cho mọi lớp của học sinh. Khi sinh phiếu học phí tháng, mọi miễn giảm còn
 * hiệu lực trong kỳ thu được cộng dồn vào {@code DISCOUNT_AMOUNT} (tối đa bằng tổng tiền phiếu).
 */
@Entity
@Table(name = "FIN_STUDENT_DISCOUNTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDiscountEntity {

    @Id
    @SequenceGenerator(name = "seq_student_discount", sequenceName = "SEQ_FIN_STUDENT_DISCOUNTS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_student_discount")
    @Column(name = "ID")
    private Long id;

    @Column(name = "STUDENT_ID", nullable = false)
    private Long studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STUDENT_ID", insertable = false, updatable = false)
    private StudentEntity student;

    @Column(name = "CLASS_ID")
    private Long classId;

    /** {@code PERCENT} hoặc {@code AMOUNT} - {@code CK_DISCOUNTS_TYPE}. */
    @Column(name = "DISCOUNT_TYPE", nullable = false, length = 20)
    private String discountType;

    @Column(name = "DISCOUNT_VALUE", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "VALID_FROM", nullable = false)
    private LocalDate validFrom;

    /** {@code null} = không thời hạn. */
    @Column(name = "VALID_TO")
    private LocalDate validTo;

    @Column(name = "REASON", length = 255)
    private String reason;

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
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
