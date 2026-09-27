package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity ánh xạ bảng {@code EDU_STUDENTS} - Module Quản lý Học sinh.
 * <p>
 * Dùng cho nghiệp vụ CRUD chuẩn hóa qua Spring Data JPA (DB-First). Nghiệp vụ tìm kiếm động
 * được xử lý bằng Standalone Procedure {@code PRC_SEARCH_STUDENTS}.
 */
@Entity
@Table(name = "EDU_STUDENTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentEntity {

    @Id
    @SequenceGenerator(name = "seq_student", sequenceName = "SEQ_EDU_STUDENTS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_student")
    @Column(name = "ID")
    private Long id;

    /**
     * Mã học sinh do Function {@code FN_NEXT_BIZ_CODE('STUDENT')} sinh lúc INSERT
     * khi client không gửi mã (form thêm mới / import Excel để trống). Import được phép
     * điền sẵn mã; trigger chỉ sinh khi {@code STUDENT_CODE} NULL. Cột bất biến sau khi tạo.
     * {@code writable = true} để Hibernate ghi mã Excel vào INSERT (trigger giữ nguyên).
     */
    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "STUDENT_CODE", nullable = false, unique = true, length = 30, insertable = true, updatable = false)
    private String studentCode;

    @Column(name = "FULL_NAME", nullable = false, length = 150)
    private String fullName;

    @Column(name = "EMAIL", length = 100)
    private String email;

    @Column(name = "DATE_OF_BIRTH")
    private LocalDate dateOfBirth;

    @Column(name = "PARENT_NAME", length = 100)
    private String parentName;

    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "ADDRESS", length = 255)
    private String address;

    @Column(name = "NOTE", length = 500)
    private String note;

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

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClassStudentEntity> enrollments = new ArrayList<>();

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY)
    @Builder.Default
    private List<AttendanceEntity> attendances = new ArrayList<>();

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY)
    @Builder.Default
    private List<GradeEntity> grades = new ArrayList<>();

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY)
    @Builder.Default
    private List<TuitionFeeEntity> tuitionFees = new ArrayList<>();

    @OneToMany(mappedBy = "convertedStudent", fetch = FetchType.LAZY)
    @Builder.Default
    private List<LeadEntity> convertedFromLeads = new ArrayList<>();

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
