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
 * Tài nguyên bài học ({@code LMS_LESSON_RESOURCES}, V21_2): FILE (module LESSON) / VIDEO / LINK / ASSIGNMENT.
 * Hình dạng mỗi loại khoá bằng {@code CK_LRES_SHAPE}.
 * Hợp đồng Luồng 0 GĐ3: Bot 1 không đổi tên/kiểu cột; thêm cột mới phải qua migration + §9 docs/portal-p3-api.md.
 */
@Entity
@Table(name = "LMS_LESSON_RESOURCES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonResourceEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_lesson_resources", sequenceName = "SEQ_LMS_LESSON_RESOURCES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_lesson_resources")
    @Column(name = "ID")
    private Long id;

    @Column(name = "LESSON_ID")
    private Long lessonId;

    @Column(name = "RESOURCE_TYPE", length = 20)
    private String resourceType;

    @Column(name = "TITLE", length = 200)
    private String title;

    @Column(name = "FILE_ID")
    private Long fileId;

    @Column(name = "URL", length = 1000)
    private String url;

    @Column(name = "EMBED_URL", length = 1000)
    private String embedUrl;

    @Column(name = "PROVIDER", length = 20)
    private String provider;

    @Column(name = "ASSIGNMENT_ID")
    private Long assignmentId;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

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
