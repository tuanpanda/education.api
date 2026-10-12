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

import com.education.base.common.file.FileConstants;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code SYS_ATTACHED_FILES} - metadata của file upload/đính kèm.
 * <p>
 * Cột {@code FILE_PATH} chỉ lưu đường dẫn TƯƠNG ĐỐI so với {@code app.storage.base-dir} (= storage key của
 * {@link com.education.base.storage.BlobStore}).
 * <p>
 * Metadata v2 (migration {@code V19_1__files_metadata_v2.sql}, hợp đồng Luồng 0 - xem {@code docs/files-api.md}):
 * trạng thái vòng đời ({@link FileConstants.Status}), người upload, học sinh sở hữu (quota), checksum, quét virus,
 * xoá mềm / xoá vĩnh viễn. {@code IS_DELETED} giữ lại để code / procedure cũ vẫn đúng: luôn bằng 1 khi
 * {@code STATUS} là {@code DELETED}/{@code PURGED}.
 * <p>
 * Chỉ Luồng A sửa lớp này sau khi hợp đồng chốt (Luồng B/D chỉ đọc các cột mới).
 */
@Entity
@Table(name = "SYS_ATTACHED_FILES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileEntity {

    @Id
    @SequenceGenerator(name = "seq_file", sequenceName = "SEQ_SYS_ATTACHED_FILES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_file")
    @Column(name = "ID")
    private Long id;

    /**
     * Tên file gốc do người dùng upload (ví dụ: {@code ho-so-nhap-hoc.pdf}).
     */
    @Column(name = "ORIGINAL_NAME", nullable = false, length = 255)
    private String originalName;

    /**
     * Tên file vật lý đã sinh ngẫu nhiên theo dạng {@code {UUID}_{tenfile}}.
     */
    @Column(name = "STORED_NAME", nullable = false, length = 255)
    private String storedName;

    /**
     * Đường dẫn TƯƠNG ĐỐI so với {@code app.storage.base-dir},
     * ví dụ: {@code STUDENT/2026/09/uuid_ho-so.pdf}.
     */
    @Column(name = "FILE_PATH", nullable = false, length = 500)
    private String filePath;

    @Column(name = "CONTENT_TYPE", nullable = false, length = 100)
    private String contentType;

    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    /**
     * Module nghiệp vụ sở hữu file (ví dụ: {@code STUDENT}, {@code COMMON}).
     */
    @Column(name = "MODULE_NAME", nullable = false, length = 50)
    private String moduleName;

    /**
     * ID bản ghi nghiệp vụ mà file này đính kèm.
     */
    @Column(name = "REFERENCE_ID")
    private Long referenceId;

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

    // ---- Metadata v2 (V19_1) -----------------------------------------------------------------------

    /** {@code ACTIVE} / {@code PENDING} / {@code DELETED} / {@code PURGED} - xem {@link FileConstants.Status}. */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    /** {@code SYS_USERS.ID} của người upload (NULL với dữ liệu cũ trước V19). */
    @Column(name = "UPLOADED_BY_USER_ID")
    private Long uploadedByUserId;

    /** {@code STAFF} / {@code STUDENT} / {@code SYSTEM} - xem {@link FileConstants.UploaderType}. */
    @Column(name = "UPLOADED_BY_TYPE", length = 20)
    private String uploadedByType;

    /** {@code EDU_STUDENTS.ID} của học sinh "sở hữu" file - dùng tính quota và lọc "file của HS X". */
    @Column(name = "OWNER_STUDENT_ID")
    private Long ownerStudentId;

    /** Đuôi file viết thường, không dấu chấm (ví dụ {@code pdf}). */
    @Column(name = "FILE_EXT", length = 10)
    private String fileExt;

    /** SHA-256 (hex thường, 64 ký tự). NULL = chưa tính (file cũ - xem checksum-backfill). */
    @Column(name = "CHECKSUM_SHA256", length = 64)
    private String checksumSha256;

    /** {@code LOCAL} / {@code S3} - xem {@link FileConstants.StorageBackend}. */
    @Column(name = "STORAGE_BACKEND", nullable = false, length = 20)
    private String storageBackend;

    /** {@code SKIPPED} / {@code PENDING} / {@code CLEAN} / {@code INFECTED} / {@code ERROR}. */
    @Column(name = "SCAN_STATUS", nullable = false, length = 20)
    private String scanStatus;

    @Column(name = "SCANNED_AT")
    private LocalDateTime scannedAt;

    @Column(name = "SCAN_DETAIL", length = 255)
    private String scanDetail;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    /** Tên đăng nhập người xoá mềm (hoặc {@code SYSTEM} với job). */
    @Column(name = "DELETED_BY", length = 50)
    private String deletedBy;

    @Column(name = "DELETE_REASON", length = 500)
    private String deleteReason;

    /** Hạn job dọn rác được xoá vĩnh viễn blob (DELETED: now + trash-days; PENDING: now + pending-hours). */
    @Column(name = "PURGE_AFTER")
    private LocalDateTime purgeAfter;

    @Column(name = "PURGED_AT")
    private LocalDateTime purgedAt;

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        // Các cột V19_1 là NOT NULL: Hibernate ghi NULL tường minh (DEFAULT của Oracle không áp dụng),
        // nên mặc định phải đặt ở đây để luồng lưu file hiện có vẫn chạy được sau migration.
        if (status == null) {
            status = isDeleted == 1 ? FileConstants.Status.DELETED : FileConstants.Status.ACTIVE;
        }
        if (storageBackend == null) {
            storageBackend = FileConstants.StorageBackend.LOCAL;
        }
        if (scanStatus == null) {
            scanStatus = FileConstants.ScanStatus.SKIPPED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
