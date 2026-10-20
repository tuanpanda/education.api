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

/** GĐ4 - sự kiện chống gian lận cơ bản (V22_3). */
@Entity
@Table(name = "LMS_QUIZ_ATTEMPT_EVENTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttemptEventEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_quiz_attempt_events", sequenceName = "SEQ_LMS_QUIZ_ATTEMPT_EVENTS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_quiz_attempt_events")
    @Column(name = "ID")
    private Long id;

    @Column(name = "ATTEMPT_ID")
    private Long attemptId;

    @Column(name = "EVENT_TYPE")
    private String eventType;

    @Column(name = "OCCURRED_AT")
    private LocalDateTime occurredAt;

    @Column(name = "IP_ADDRESS")
    private String ipAddress;

    @Column(name = "USER_AGENT")
    private String userAgent;

    @PrePersist
    void onCreate() {
    }
}
