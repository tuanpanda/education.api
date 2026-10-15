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

/** GĐ4 - đáp án theo câu của attempt (V22_3). SNAPSHOT = câu tại lúc bốc đề. */
@Entity
@Table(name = "LMS_QUIZ_ANSWERS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAnswerEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_quiz_answers", sequenceName = "SEQ_LMS_QUIZ_ANSWERS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_quiz_answers")
    @Column(name = "ID")
    private Long id;

    @Column(name = "ATTEMPT_ID")
    private Long attemptId;

    @Column(name = "QUESTION_ID")
    private Long questionId;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "POINTS")
    private BigDecimal points;

    @Column(name = "SELECTED_OPTION_IDS")
    private String selectedOptionIds;

    @Lob
    @Column(name = "TEXT_ANSWER")
    private String textAnswer;

    @Column(name = "IS_CORRECT")
    private Integer isCorrect;

    @Column(name = "POINTS_AWARDED")
    private BigDecimal pointsAwarded;

    @Column(name = "GRADED_BY")
    private String gradedBy;

    @Column(name = "GRADED_AT")
    private LocalDateTime gradedAt;

    @Column(name = "SAVED_AT")
    private LocalDateTime savedAt;

    @Lob
    @Column(name = "SNAPSHOT")
    private String snapshot;

    @Column(name = "GRADER_COMMENT")
    private String graderComment;

    @Column(name = "OVERRIDE_REASON")
    private String overrideReason;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
    }
}
