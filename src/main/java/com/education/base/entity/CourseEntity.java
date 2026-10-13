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
 * Khoá học mẫu ({@code LMS_COURSES}, V21_1): bộ chương/bài dùng chung cho nhiều lớp ({@code EDU_CLASSES.COURSE_ID}).
 * Hợp đồng Luồng 0 GĐ3: Bot 1 không đổi tên/kiểu cột; thêm cột mới phải qua migration + §9 docs/portal-p3-api.md.
 */
@Entity
@Table(name = "LMS_COURSES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseEntity {

    @Id
    @SequenceGenerator(name = "seq_lms_courses", sequenceName = "SEQ_LMS_COURSES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lms_courses")
    @Column(name = "ID")
    private Long id;

    @Column(name = "COURSE_CODE", length = 50)
    private String courseCode;

    @Column(name = "COURSE_NAME", length = 200)
    private String courseName;

    @Column(name = "SUBJECT_NAME", length = 150)
    private String subjectName;

    @Column(name = "GRADE_LEVEL")
    private Integer gradeLevel;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "STATUS", length = 20)
    private String status;

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
        if (status == null) {
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
