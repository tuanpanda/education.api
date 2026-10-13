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

import java.time.LocalDateTime;

/**
 * Bài học ({@code LMS_LESSONS}, V21_2). {@code courseId}/{@code classId} chép từ chương; {@code sessionId} chỉ khi thuộc lớp.
 * Giờ lưu = giờ server Asia/Ho_Chi_Minh.
 * Hợp đồng Luồng 0 GĐ3: Bot 1 không đổi tên/kiểu cột; thêm cột mới phải qua migration + §9 docs/portal-p3-api.md.
 */
@Entity
@Table(name = "LMS_LESSONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_lessons", sequenceName = "SEQ_LMS_LESSONS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_lessons")
    @Column(name = "ID")
    private Long id;

    @Column(name = "SECTION_ID")
    private Long sectionId;

    @Column(name = "COURSE_ID")
    private Long courseId;

    @Column(name = "CLASS_ID")
    private Long classId;

    @Column(name = "SESSION_ID")
    private Long sessionId;

    @Column(name = "TITLE", length = 200)
    private String title;

    @Lob
    @Column(name = "CONTENT")
    private String content;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "AVAILABLE_FROM")
    private LocalDateTime availableFrom;

    @Column(name = "ESTIMATED_MINUTES")
    private Integer estimatedMinutes;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    @Column(name = "SOURCE_LESSON_ID")
    private Long sourceLessonId;

    @Version
    @Column(name = "VERSION")
    private Long version;

    @Column(name = "IS_DELETED")
    private Integer isDeleted;

    @Column(name = "CREATED_AT")
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
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (status == null) {
            status = "DRAFT";
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
