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

/** GĐ4 - lựa chọn của câu hỏi (V22_1). */
@Entity
@Table(name = "LMS_QUESTION_OPTIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOptionEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_question_options", sequenceName = "SEQ_LMS_QUESTION_OPTIONS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_question_options")
    @Column(name = "ID")
    private Long id;

    @Column(name = "QUESTION_ID")
    private Long questionId;

    @Lob
    @Column(name = "CONTENT")
    private String content;

    @Column(name = "IS_CORRECT")
    private Integer isCorrect;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @PrePersist
    void onCreate() {
    }
}
