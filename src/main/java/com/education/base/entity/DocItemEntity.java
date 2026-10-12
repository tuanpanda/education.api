package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * Tài liệu trong kho ({@code DOC_ITEMS}, V19_4). Một tài liệu = một file {@code SYS_ATTACHED_FILES} module
 * {@code LIBRARY} ({@code REFERENCE_ID = ID}). ID lấy trước từ {@code SEQ_DOC_ITEMS} để upload file trước khi
 * chèn dòng ({@code FILE_ID NOT NULL}).
 */
@Entity
@Table(name = "DOC_ITEMS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocItemEntity implements Persistable<Long> {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "FOLDER_ID")
    private Long folderId;

    @Column(name = "FILE_ID", nullable = false)
    private Long fileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FILE_ID", insertable = false, updatable = false)
    private FileEntity file;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    /** Dạng {@code ,a,b,} chữ thường; NULL khi không có thẻ. */
    @Column(name = "TAGS", length = 500)
    private String tags;

    @Column(name = "VERSION_NO", nullable = false)
    private Integer versionNo;

    @Column(name = "VERSION_NOTE", length = 500)
    private String versionNote;

    @Column(name = "OWNER_USER_ID", nullable = false)
    private Long ownerUserId;

    @Column(name = "IS_DELETED", nullable = false)
    private Integer isDeleted;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY", length = 50)
    private String deletedBy;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Transient
    @Builder.Default
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (versionNo == null) {
            versionNo = 1;
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