package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Giới hạn upload theo module file - bảng {@code SYS_FILE_MODULE_SETTINGS} (V19_2). Luồng B.
 */
@Entity
@Table(name = "SYS_FILE_MODULE_SETTINGS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileModuleSettingEntity {

    @Id
    @Column(name = "MODULE_NAME", length = 50)
    private String moduleName;

    @Column(name = "MAX_FILE_SIZE_BYTES", nullable = false)
    private Long maxFileSizeBytes;

    @Column(name = "MAX_FILES_PER_REF")
    private Integer maxFilesPerRef;

    @Column(name = "MAX_TOTAL_BYTES_PER_REF")
    private Long maxTotalBytesPerRef;

    /** CSV đuôi chữ thường; NULL = theo SafeFileType. */
    @Column(name = "ALLOWED_EXTENSIONS", length = 200)
    private String allowedExtensions;

    @Column(name = "TRASH_RETENTION_DAYS", nullable = false)
    private Integer trashRetentionDays;

    /** 0/1. */
    @Column(name = "COUNT_TO_STUDENT_QUOTA", nullable = false)
    private Integer countToStudentQuota;

    /** 0/1. */
    @Column(name = "IS_ACTIVE", nullable = false)
    private Integer isActive;

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
        if (trashRetentionDays == null) {
            trashRetentionDays = 30;
        }
        if (countToStudentQuota == null) {
            countToStudentQuota = 0;
        }
        if (isActive == null) {
            isActive = 1;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
