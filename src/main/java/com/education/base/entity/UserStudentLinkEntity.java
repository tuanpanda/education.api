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

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code EDU_USER_STUDENT_LINKS} (V17_1) - liên kết tài khoản {@code SYS_USERS} với học sinh
 * {@code EDU_STUDENTS}.
 * <ul>
 *     <li>{@code RELATION = SELF}: tài khoản của chính học sinh. Mỗi học sinh có tối đa MỘT liên kết {@code SELF}
 *     đang hoạt động ({@code STATUS = ACTIVE}, {@code IS_DELETED = 0}) - unique index {@code UX_USL_SELF_ACTIVE}.</li>
 *     <li>{@code RELATION = PARENT}: tài khoản phụ huynh (giai đoạn sau, 1 phụ huynh - nhiều con).</li>
 * </ul>
 * Unique {@code (USER_ID, STUDENT_ID)} ({@code UQ_USL_USER_STUDENT}).
 */
@Entity
@Table(name = "EDU_USER_STUDENT_LINKS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStudentLinkEntity {

    @Id
    @SequenceGenerator(name = "seq_user_student_link", sequenceName = "SEQ_EDU_USER_STUDENT_LINKS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_user_student_link")
    @Column(name = "ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", insertable = false, updatable = false)
    private UserEntity user;

    @Column(name = "STUDENT_ID", nullable = false)
    private Long studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STUDENT_ID", insertable = false, updatable = false)
    private StudentEntity student;

    /** {@code SELF} / {@code PARENT} ({@code CK_USL_RELATION}). */
    @Column(name = "RELATION", nullable = false, length = 20)
    private String relation;

    /** 1 = liên kết chính (ví dụ học sinh mặc định của phụ huynh nhiều con). */
    @Column(name = "IS_PRIMARY", nullable = false)
    private Integer isPrimary;

    /** {@code ACTIVE} / {@code INACTIVE} ({@code CK_USL_STATUS}). */
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

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (isPrimary == null) {
            isPrimary = 0;
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
