package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/**
 * Tiến độ học ({@code LMS_LESSON_PROGRESS}, V21_3), 1 dòng / học sinh / bài. Không có dòng = NOT_STARTED.
 * COMPLETED không lùi về IN_PROGRESS (chỉ cập nhật LAST_VIEWED_AT / VIEW_COUNT).
 * Hợp đồng Luồng 0 GĐ3: Bot 1 không đổi tên/kiểu cột; thêm cột mới phải qua migration + §9 docs/portal-p3-api.md.
 */
@Entity
@Table(name = "LMS_LESSON_PROGRESS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonProgressEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_lesson_progress", sequenceName = "SEQ_LMS_LESSON_PROGRESS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_lesson_progress")
    @Column(name = "ID")
    private Long id;

    @Column(name = "LESSON_ID")
    private Long lessonId;

    @Column(name = "STUDENT_ID")
    private Long studentId;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "FIRST_VIEWED_AT")
    private LocalDateTime firstViewedAt;

    @Column(name = "LAST_VIEWED_AT")
    private LocalDateTime lastViewedAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Column(name = "VIEW_COUNT")
    private Integer viewCount;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = "IN_PROGRESS";
        }
        if (viewCount == null) {
            viewCount = 1;
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
