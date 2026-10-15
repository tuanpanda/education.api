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

/** GĐ4 - câu chọn tay của đề (V22_2). SNAPSHOT chụp khi publish. */
@Entity
@Table(name = "LMS_QUIZ_QUESTIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestionEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_quiz_questions", sequenceName = "SEQ_LMS_QUIZ_QUESTIONS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_quiz_questions")
    @Column(name = "ID")
    private Long id;

    @Column(name = "QUIZ_ID")
    private Long quizId;

    @Column(name = "QUESTION_ID")
    private Long questionId;

    @Column(name = "POINTS")
    private BigDecimal points;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Lob
    @Column(name = "SNAPSHOT")
    private String snapshot;

    @PrePersist
    void onCreate() {
    }
}
