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
 * Entity ánh xạ bảng {@code EDU_LEADS} - nguồn tuyển sinh.
 */
@Entity
@Table(name = "EDU_LEADS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadEntity {

    @Id
    @SequenceGenerator(name = "seq_lead", sequenceName = "SEQ_EDU_LEADS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_lead")
    @Column(name = "ID")
    private Long id;

    @Column(name = "LEAD_CODE", nullable = false, unique = true, length = 30)
    private String leadCode;

    @Column(name = "FULL_NAME", nullable = false, length = 150)
    private String fullName;

    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "EMAIL", length = 100)
    private String email;

    @Column(name = "SOURCE", nullable = false, length = 30)
    private String source;

    @Column(name = "INTERESTED_SUBJECT", length = 150)
    private String interestedSubject;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "ASSIGNED_TO_ID")
    private Long assignedToId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ASSIGNED_TO_ID", insertable = false, updatable = false)
    private UserEntity assignedTo;

    @Column(name = "CONVERTED_STUDENT_ID")
    private Long convertedStudentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONVERTED_STUDENT_ID", insertable = false, updatable = false)
    private StudentEntity convertedStudent;

    @Column(name = "NEXT_FOLLOW_UP_DATE")
    private LocalDate nextFollowUpDate;

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
        if (source == null || source.isBlank()) {
            source = "OTHER";
        }
        if (status == null || status.isBlank()) {
            status = "NEW";
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
