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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity ánh xạ bảng {@code EDU_CLASSES} - lớp học thuộc phân hệ Đào tạo.
 * <p>
 * Cột khóa ngoại được map tường minh bằng {@code @Column} (DB-First) để ghi dữ liệu;
 * quan hệ {@code @ManyToOne}/{@code @OneToMany} chỉ đọc, {@code FetchType.LAZY}.
 */
@Entity
@Table(name = "EDU_CLASSES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassEntity {

    @Id
    @SequenceGenerator(name = "seq_class", sequenceName = "SEQ_EDU_CLASSES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_class")
    @Column(name = "ID")
    private Long id;

    /**
     * Mã lớp do Function {@code FN_NEXT_BIZ_CODE('CLASS', GRADE_LEVEL)} sinh lúc INSERT.
     * Quy luật mặc định {@code {PREFIX}{GRADE}{YYYY}{SEQ}} — ví dụ lớp 9: {@code LH920260001}.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "CLASS_CODE", nullable = false, unique = true, length = 30,
            insertable = false, updatable = false)
    private String classCode;

    @Column(name = "CLASS_NAME", nullable = false, length = 150)
    private String className;

    /** Khối lớp (1–12), chèn vào mã sau tiền tố chữ qua token {@code {GRADE}}. */
    @Column(name = "GRADE_LEVEL")
    private Integer gradeLevel;

    @Column(name = "SUBJECT_NAME", length = 150)
    private String subjectName;

    @Column(name = "TEACHER_ID")
    private Long teacherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TEACHER_ID", insertable = false, updatable = false)
    private UserEntity teacher;

    @Column(name = "ROOM_NAME", length = 50)
    private String roomName;

    @Column(name = "START_DATE")
    private LocalDate startDate;

    @Column(name = "END_DATE")
    private LocalDate endDate;

    @Column(name = "CAPACITY", nullable = false)
    private Integer capacity;

    @Column(name = "TUITION_AMOUNT", nullable = false)
    private BigDecimal tuitionAmount;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    /** Màu hiển thị trên lịch học, dạng {@code #RRGGBB}. */
    @Column(name = "CALENDAR_COLOR", length = 7)
    private String calendarColor;

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

    @OneToMany(mappedBy = "clazz", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClassStudentEntity> enrollments = new ArrayList<>();

    @OneToMany(mappedBy = "clazz", fetch = FetchType.LAZY)
    @Builder.Default
    private List<AttendanceEntity> attendances = new ArrayList<>();

    @OneToMany(mappedBy = "clazz", fetch = FetchType.LAZY)
    @Builder.Default
    private List<GradeEntity> grades = new ArrayList<>();

    @OneToMany(mappedBy = "clazz", fetch = FetchType.LAZY)
    @Builder.Default
    private List<TuitionFeeEntity> tuitionFees = new ArrayList<>();

    @OneToMany(mappedBy = "clazz", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClassScheduleEntity> schedules = new ArrayList<>();

    @OneToMany(mappedBy = "clazz", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClassSessionEntity> sessions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (capacity == null) {
            capacity = 0;
        }
        if (tuitionAmount == null) {
            tuitionAmount = BigDecimal.ZERO;
        }
        if (status == null || status.isBlank()) {
            status = "PLANNED";
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
