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
 * Entity ánh xạ bảng {@code SYS_ATTACHED_FILES} - metadata của file upload/đính kèm.
 * <p>
 * Cột {@code FILE_PATH} chỉ lưu đường dẫn TƯƠNG ĐỐI so với {@code app.storage.base-dir}.
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
