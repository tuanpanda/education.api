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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Chương ({@code LMS_LESSON_SECTIONS}, V21_2). Đúng một trong {@code courseId} (khoá mẫu) / {@code classId} (lớp).
 * Hợp đồng Luồng 0 GĐ3: Bot 1 không đổi tên/kiểu cột; thêm cột mới phải qua migration + §9 docs/portal-p3-api.md.
 */
@Entity
@Table(name = "LMS_LESSON_SECTIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonSectionEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_lesson_sections", sequenceName = "SEQ_LMS_LESSON_SECTIONS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_lesson_sections")
    @Column(name = "ID")
    private Long id;

    @Column(name = "COURSE_ID")
    private Long courseId;

    @Column(name = "CLASS_ID")
    private Long classId;

    @Column(name = "TITLE", length = 200)
    private String title;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "SOURCE_SECTION_ID")
    private Long sourceSectionId;

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
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
