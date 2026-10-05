package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
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

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code EDU_ANNOUNCEMENTS} (V18_1) - thông báo cổng học sinh / nhân viên.
 */
@Entity
@Table(name = "EDU_ANNOUNCEMENTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementEntity {

    @Id
    @SequenceGenerator(name = "seq_announcement", sequenceName = "SEQ_EDU_ANNOUNCEMENTS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_announcement")
    @Column(name = "ID")
    private Long id;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private String content;

    /** {@code ALL} / {@code CLASS} ({@code CK_ANN_SCOPE}). */
    @Column(name = "SCOPE_TYPE", nullable = false, length = 20)
    private String scopeType;

    @Column(name = "CLASS_ID")
    private Long classId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLASS_ID", insertable = false, updatable = false)
    private ClassEntity clazz;

    /** {@code STUDENT} / {@code PARENT} / {@code ALL} ({@code CK_ANN_AUDIENCE}). */
    @Column(name = "AUDIENCE", nullable = false, length = 20)
    private String audience;

    @Column(name = "IS_PINNED", nullable = false)
    private Integer isPinned;

    /** {@code DRAFT} / {@code PUBLISHED} / {@code ARCHIVED} ({@code CK_ANN_STATUS}). */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;

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
        if (isPinned == null) {
            isPinned = 0;
        }
        if (status == null || status.isBlank()) {
            status = "DRAFT";
        }
        if (audience == null || audience.isBlank()) {
            audience = "STUDENT";
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
