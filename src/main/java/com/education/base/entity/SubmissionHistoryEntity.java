package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lịch sử nộp ({@code LMS_SUBMISSION_HISTORY}, V20_2): 1 dòng / lần bấm Nộp; UNIQUE (SUBMISSION_ID, ATTEMPT_NO).
 * Khi giáo viên trả bài / chấm, dòng của lần nộp hiện tại được cập nhật OUTCOME + SCORE/FEEDBACK.
 */
@Entity
@Table(name = "LMS_SUBMISSION_HISTORY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionHistoryEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_submission_hist", sequenceName = "SEQ_LMS_SUBMISSION_HISTORY", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_submission_hist")
    @Column(name = "ID")
    private Long id;

    @Column(name = "SUBMISSION_ID", nullable = false)
    private Long submissionId;

    @Column(name = "ATTEMPT_NO", nullable = false)
    private Integer attemptNo;

    @Lob
    @Column(name = "CONTENT_SNAPSHOT")
    private String contentSnapshot;

    /** CSV {@code SYS_ATTACHED_FILES.ID} module SUBMISSION tại thời điểm nộp. */
    @Column(name = "FILE_IDS", length = 400)
    private String fileIds;

    @Column(name = "SUBMITTED_AT", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "IS_LATE", nullable = false)
    private Integer isLate;

    /** {@code SUBMITTED} / {@code RETURNED} / {@code GRADED}. */
    @Column(name = "OUTCOME", nullable = false, length = 20)
    private String outcome;

    @Column(name = "SCORE", precision = 5, scale = 2)
    private BigDecimal score;

    @Lob
    @Column(name = "FEEDBACK")
    private String feedback;

    @Column(name = "ACTED_BY")
    private Long actedBy;

    @Column(name = "ACTED_AT")
    private LocalDateTime actedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (isLate == null) {
            isLate = 0;
        }
        if (outcome == null || outcome.isBlank()) {
            outcome = "SUBMITTED";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
