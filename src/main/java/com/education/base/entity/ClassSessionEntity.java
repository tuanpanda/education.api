package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity ánh xạ {@code EDU_CLASS_SESSIONS} — buổi học thực tế theo ngày cụ thể.
 * Trạng thái: {@code SCHEDULED} / {@code COMPLETED} / {@code CANCELLED}.
 */
@Entity
@Table(name = "EDU_CLASS_SESSIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSessionEntity {

    @Id
    @SequenceGenerator(name = "seq_class_session", sequenceName = "SEQ_EDU_CLASS_SESSIONS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_class_session")
    @Column(name = "ID")
    private Long id;

    @Column(name = "CLASS_ID", nullable = false)
    private Long classId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLASS_ID", insertable = false, updatable = false)
    private ClassEntity clazz;

    @Column(name = "SCHEDULE_ID")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SCHEDULE_ID", insertable = false, updatable = false)
    private ClassScheduleEntity schedule;

    @Column(name = "SESSION_DATE", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "START_TIME", nullable = false, length = 5)
    private String startTime;

    @Column(name = "END_TIME", nullable = false, length = 5)
    private String endTime;

    @Column(name = "ROOM_NAME", length = 50)
    private String roomName;

    @Column(name = "TEACHER_ID")
    private Long teacherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TEACHER_ID", insertable = false, updatable = false)
    private UserEntity teacher;

    @Column(name = "TOPIC", length = 200)
    private String topic;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "NOTE", length = 500)
    private String note;

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
            status = "SCHEDULED";
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
