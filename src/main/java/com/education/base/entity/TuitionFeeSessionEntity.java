package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
 * Entity ánh xạ {@code FIN_TUITION_FEE_SESSIONS} (V23_4): một buổi điểm danh tính phí đã nằm trên một phiếu.
 * <p>
 * Ảnh chụp lúc tạo phiếu: ngày trên phiếu đọc từ bảng này, không đọc lại điểm danh. Hủy / xóa phiếu xóa các dòng
 * của phiếu để giải phóng ngày. {@code UQ_FEE_SESSIONS_ATTENDANCE}: một buổi chỉ nằm trên tối đa một phiếu.
 */
@Entity
@Table(name = "FIN_TUITION_FEE_SESSIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuitionFeeSessionEntity {

    @Id
    @SequenceGenerator(name = "seq_tuition_fee_session", sequenceName = "SEQ_FIN_TUITION_FEE_SESSIONS",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tuition_fee_session")
    @Column(name = "ID")
    private Long id;

    @Column(name = "FEE_ID", nullable = false)
    private Long feeId;

    @Column(name = "ATTENDANCE_ID", nullable = false)
    private Long attendanceId;

    @Column(name = "SESSION_DATE", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
