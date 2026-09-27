package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code FIN_BANK_ACCOUNTS} - số tài khoản thụ hưởng VietQR.
 * Chỉ một dòng {@code IS_ACTIVE = 1} (chưa xóa mềm) được phép tồn tại.
 */
@Entity
@Table(name = "FIN_BANK_ACCOUNTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountEntity {

    @Id
    @SequenceGenerator(name = "seq_bank_account", sequenceName = "SEQ_FIN_BANK_ACCOUNTS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_bank_account")
    @Column(name = "ID")
    private Long id;

    @Column(name = "ACCOUNT_CODE", nullable = false, length = 30)
    private String accountCode;

    @Column(name = "BANK_BIN", nullable = false, length = 20)
    private String bankBin;

    @Column(name = "BANK_NAME", nullable = false, length = 100)
    private String bankName;

    @Column(name = "ACCOUNT_NO", nullable = false, length = 30)
    private String accountNo;

    @Column(name = "ACCOUNT_NAME", nullable = false, length = 150)
    private String accountName;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Integer isActive;

    @Column(name = "NOTE", length = 255)
    private String note;

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
        if (isActive == null) {
            isActive = 0;
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
