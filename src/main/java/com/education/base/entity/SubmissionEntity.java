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
 * Bài nộp ({@code LMS_SUBMISSIONS}, V20_2): 1 dòng / học sinh / bài, UNIQUE (ASSIGNMENT_ID, STUDENT_ID).
 * File: {@code SYS_ATTACHED_FILES} MODULE_NAME = SUBMISSION (bài làm) / FEEDBACK (nhận xét), REFERENCE_ID = {@link #id}.
 * {@link #version} = optimistic lock khi chấm (client gửi {@code expectedVersion}).
 */
@Entity
@Table(name = "LMS_SUBMISSIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_submission", sequenceName = "SEQ_LMS_SUBMISSIONS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_submission")
    @Column(name = "ID")
    private Long id;

    @Column(name = "ASSIGNMENT_ID", nullable = false)
    private Long assignmentId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Long studentId;

    /** Bài làm dạng văn bản (HTML đã sanitize). */
    @Lob
    @Column(name = "CONTENT_TEXT")
    private String contentText;

    /** {@code DRAFT} / {@code SUBMITTED} / {@code RETURNED} / {@code GRADED}. */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    /** Số lần đã bấm Nộp (= số dòng LMS_SUBMISSION_HISTORY). */
    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private Integer attemptCount;

    @Column(name = "FIRST_SUBMITTED_AT")
    private LocalDateTime firstSubmittedAt;

    @Column(name = "SUBMITTED_AT")
    private LocalDateTime submittedAt;

    @Column(name = "IS_LATE", nullable = false)
    private Integer isLate;

    @Column(name = "SCORE", precision = 5, scale = 2)
    private BigDecimal score;

    @Lob
    @Column(name = "FEEDBACK")
    private String feedback;

    /** {@code SYS_USERS.ID}. */
    @Column(name = "GRADED_BY")
    private Long gradedBy;

    @Column(name = "GRADED_AT")
    private LocalDateTime gradedAt;

    @Column(name = "RETURNED_AT")
    private LocalDateTime returnedAt;

    @Column(name = "RETURN_REASON", length = 500)
    private String returnReason;

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
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (isLate == null) {
            isLate = 0;
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
