package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity ánh xạ {@code EDU_CLASS_SCHEDULES} — khung lịch cố định hàng tuần của lớp.
 * {@code DAY_OF_WEEK}: 2=Thứ Hai … 8=Chủ Nhật. Giờ {@code HH:mm}.
 */
@Entity
@Table(name = "EDU_CLASS_SCHEDULES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassScheduleEntity {

    @Id
    @SequenceGenerator(name = "seq_class_schedule", sequenceName = "SEQ_EDU_CLASS_SCHEDULES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_class_schedule")
    @Column(name = "ID")
    private Long id;

    @Column(name = "CLASS_ID", nullable = false)
    private Long classId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLASS_ID", insertable = false, updatable = false)
    private ClassEntity clazz;

    @Column(name = "DAY_OF_WEEK", nullable = false)
    private Integer dayOfWeek;

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

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

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

    @OneToMany(mappedBy = "schedule", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClassSessionEntity> sessions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
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
