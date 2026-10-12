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

/** Chia sẻ thư mục / tài liệu ({@code DOC_SHARES}, V19_4). Không xoá mềm. */
@Entity
@Table(name = "DOC_SHARES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocShareEntity {

    @Id
    @SequenceGenerator(name = "seq_doc_share", sequenceName = "SEQ_DOC_SHARES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_doc_share")
    @Column(name = "ID")
    private Long id;

    @Column(name = "FOLDER_ID")
    private Long folderId;

    @Column(name = "ITEM_ID")
    private Long itemId;

    @Column(name = "TARGET_TYPE", nullable = false, length = 20)
    private String targetType;

    @Column(name = "ROLE_ID")
    private Long roleId;

    @Column(name = "CLASS_ID")
    private Long classId;

    @Column(name = "VISIBLE_TO_STUDENTS", nullable = false)
    private Integer visibleToStudents;

    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;

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
        if (visibleToStudents == null) {
            visibleToStudents = 0;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Đích của chia sẻ: ROLE_ID / CLASS_ID tuỳ loại, NULL với ALL_STAFF. */
    public Long targetId() {
        if ("ROLE".equals(targetType)) {
            return roleId;
        }
        if ("CLASS".equals(targetType)) {
            return classId;
        }
        return null;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}