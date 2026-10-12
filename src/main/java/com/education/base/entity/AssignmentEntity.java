package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bài tập về nhà ({@code LMS_ASSIGNMENTS}, V20_1). Giờ lưu = giờ server Asia/Ho_Chi_Minh (không múi giờ).
 * Hợp đồng Luồng 0: Bot 1 không đổi tên/kiểu cột; thêm cột mới phải qua migration + "Yêu cầu thay đổi hợp đồng".
 */
@Entity
@Table(name = "LMS_ASSIGNMENTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_assignment", sequenceName = "SEQ_LMS_ASSIGNMENTS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_assignment")
    @Column(name = "ID")
    private Long id;

    @Column(name = "CLASS_ID", nullable = false)
    private Long classId;

    /** Buổi học ({@code EDU_CLASS_SESSIONS.ID}) - tuỳ chọn, phải cùng lớp. */
    @Column(name = "SESSION_ID")
    private Long sessionId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    /** HTML CKEditor đã sanitize. */
    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    /** {@code TEXT} / {@code FILE} / {@code BOTH}. */
    @Column(name = "SUBMISSION_TYPE", nullable = false, length = 10)
    private String submissionType;

    @Column(name = "MAX_SCORE", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "DUE_AT", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "ALLOW_LATE", nullable = false)
    private Integer allowLate;

    /** Hạn cuối nộp muộn; NULL + ALLOW_LATE=1 = nộp muộn tới khi CLOSED. */
    @Column(name = "LATE_UNTIL")
    private LocalDateTime lateUntil;

    @Column(name = "MAX_FILES", nullable = false)
    private Integer maxFiles;

    @Column(name = "ALLOW_RESUBMIT", nullable = false)
    private Integer allowResubmit;

    @Column(name = "SYNC_TO_GRADEBOOK", nullable = false)
    private Integer syncToGradebook;

    @Column(name = "GRADE_WEIGHT", nullable = false, precision = 5, scale = 2)
    private BigDecimal gradeWeight;

    /** {@code DRAFT} / {@code PUBLISHED} / {@code CLOSED}. */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    @Column(name = "CLOSED_AT")
    private LocalDateTime closedAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

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
        if (status == null || status.isBlank()) {
            status = "DRAFT";
        }
        if (submissionType == null || submissionType.isBlank()) {
            submissionType = "BOTH";
        }
        if (maxScore == null) {
            maxScore = BigDecimal.TEN;
        }
        if (allowLate == null) {
            allowLate = 0;
        }
        if (maxFiles == null) {
            maxFiles = 5;
        }
        if (allowResubmit == null) {
            allowResubmit = 1;
        }
        if (syncToGradebook == null) {
            syncToGradebook = 0;
        }
        if (gradeWeight == null) {
            gradeWeight = BigDecimal.ONE;
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
