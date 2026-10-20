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

/** GĐ4 - câu hỏi ngân hàng (V22_1). */
@Entity
@Table(name = "LMS_QUESTIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_questions", sequenceName = "SEQ_LMS_QUESTIONS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_questions")
    @Column(name = "ID")
    private Long id;

    @Column(name = "QUESTION_TYPE")
    private String questionType;

    @Column(name = "SUBJECT_NAME")
    private String subjectName;

    @Column(name = "GRADE_LEVEL")
    private String gradeLevel;

    @Column(name = "TOPIC")
    private String topic;

    @Column(name = "DIFFICULTY")
    private Integer difficulty;

    @Lob
    @Column(name = "CONTENT")
    private String content;

    @Lob
    @Column(name = "EXPLANATION")
    private String explanation;

    @Lob
    @Column(name = "ACCEPTED_ANSWERS")
    private String acceptedAnswers;

    @Column(name = "OWNER_ID")
    private Long ownerId;

    @Column(name = "VISIBILITY")
    private String visibility;

    @Column(name = "COURSE_ID")
    private Long courseId;

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
        if (visibility == null) {
            visibility = "PRIVATE";
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
