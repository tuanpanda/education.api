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

/** GĐ4 - đề kiểm tra (V22_2). Giờ = giờ server Asia/Ho_Chi_Minh. */
@Entity
@Table(name = "LMS_QUIZZES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_quizzes", sequenceName = "SEQ_LMS_QUIZZES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_quizzes")
    @Column(name = "ID")
    private Long id;

    @Column(name = "CLASS_ID")
    private Long classId;

    @Column(name = "LESSON_ID")
    private Long lessonId;

    @Column(name = "TITLE")
    private String title;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "OPEN_AT")
    private LocalDateTime openAt;

    @Column(name = "CLOSE_AT")
    private LocalDateTime closeAt;

    @Column(name = "DURATION_MIN")
    private Integer durationMin;

    @Column(name = "MAX_ATTEMPTS")
    private Integer maxAttempts;

    @Column(name = "SHUFFLE_QUESTIONS")
    private Integer shuffleQuestions;

    @Column(name = "SHUFFLE_OPTIONS")
    private Integer shuffleOptions;

    @Column(name = "RESULT_MODE")
    private String resultMode;

    @Column(name = "RESULTS_RELEASED_AT")
    private LocalDateTime resultsReleasedAt;

    @Column(name = "SCORE_POLICY")
    private String scorePolicy;

    @Column(name = "PASS_SCORE")
    private BigDecimal passScore;

    @Column(name = "MAX_SCORE")
    private BigDecimal maxScore;

    @Column(name = "SYNC_TO_GRADEBOOK")
    private Integer syncToGradebook;

    @Column(name = "STATUS")
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
        if (status == null) {
            status = "DRAFT";
        }
        if (syncToGradebook == null) {
            syncToGradebook = 0;
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
