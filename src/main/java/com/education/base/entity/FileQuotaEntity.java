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
 * Hạn mức dung lượng - bảng {@code SYS_FILE_QUOTAS} (V19_2). Luồng B.
 */
@Entity
@Table(name = "SYS_FILE_QUOTAS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileQuotaEntity {

    @Id
    @SequenceGenerator(name = "seq_file_quota", sequenceName = "SEQ_SYS_FILE_QUOTAS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_file_quota")
    @Column(name = "ID")
    private Long id;

    /** GLOBAL / STUDENT_DEFAULT / STUDENT / USER. */
    @Column(name = "SCOPE_TYPE", nullable = false, length = 20)
    private String scopeType;

    @Column(name = "SCOPE_ID")
    private Long scopeId;

    @Column(name = "MAX_BYTES", nullable = false)
    private Long maxBytes;

    @Column(name = "WARN_PERCENT", nullable = false)
    private Integer warnPercent;

    @Column(name = "NOTE", length = 500)
    private String note;

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
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (warnPercent == null) {
            warnPercent = 80;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
