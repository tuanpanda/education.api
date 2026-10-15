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

/** GĐ4 - lượt làm bài (V22_3). */
@Entity
@Table(name = "LMS_QUIZ_ATTEMPTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttemptEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_quiz_attempts", sequenceName = "SEQ_LMS_QUIZ_ATTEMPTS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_quiz_attempts")
    @Column(name = "ID")
    private Long id;

    @Column(name = "QUIZ_ID")
    private Long quizId;

    @Column(name = "STUDENT_ID")
    private Long studentId;

    @Column(name = "ATTEMPT_NO")
    private Integer attemptNo;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "DEADLINE_AT")
    private LocalDateTime deadlineAt;

    @Column(name = "SUBMITTED_AT")
    private LocalDateTime submittedAt;

    @Column(name = "SCORE")
    private BigDecimal score;

    @Column(name = "MAX_SCORE")
    private BigDecimal maxScore;

    @Lob
    @Column(name = "QUESTION_ORDER")
    private String questionOrder;

    @Column(name = "IP_ADDRESS")
    private String ipAddress;

    @Column(name = "USER_AGENT")
    private String userAgent;

    @Column(name = "FOCUS_LOST_COUNT")
    private Integer focusLostCount;

    @Column(name = "FLAGS")
    private String flags;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

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
        if (focusLostCount == null) {
            focusLostCount = 0;
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
