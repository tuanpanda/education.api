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

/** GĐ4 - quy tắc bốc câu ngẫu nhiên (V22_2). CANDIDATE_SNAPSHOT khoá khi publish. */
@Entity
@Table(name = "LMS_QUIZ_RULES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizRuleEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_quiz_rules", sequenceName = "SEQ_LMS_QUIZ_RULES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_quiz_rules")
    @Column(name = "ID")
    private Long id;

    @Column(name = "QUIZ_ID")
    private Long quizId;

    @Column(name = "TOPIC")
    private String topic;

    @Column(name = "DIFFICULTY")
    private Integer difficulty;

    @Column(name = "QUESTION_COUNT")
    private Integer questionCount;

    @Column(name = "POINTS_EACH")
    private BigDecimal pointsEach;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Lob
    @Column(name = "CANDIDATE_SNAPSHOT")
    private String candidateSnapshot;

    @PrePersist
    void onCreate() {
    }
}
