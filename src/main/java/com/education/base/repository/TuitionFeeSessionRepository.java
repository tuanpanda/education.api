package com.education.base.repository;

import com.education.base.entity.TuitionFeeSessionEntity;
import com.education.base.repository.projection.FeeSessionConflictRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Repository JPA của {@code FIN_TUITION_FEE_SESSIONS} (V23_4).
 */
public interface TuitionFeeSessionRepository extends JpaRepository<TuitionFeeSessionEntity, Long> {

    /**
     * Buổi tính phí của lớp trong khoảng ngày đã nằm trên phiếu còn hiệu lực ({@code IS_DELETED = 0},
     * {@code STATUS <> 'CANCELLED'}).
     * <p>
     * Lọc bằng JOIN theo lớp + khoảng ngày, KHÔNG truyền danh sách ID điểm danh vào {@code IN (...)}
     * (tránh ORA-01795 khi quá 1000 buổi).
     */
    @Query("SELECT new com.education.base.repository.projection.FeeSessionConflictRow("
            + "s.attendanceId, f.studentId, s.sessionDate, f.id, f.feeCode)"
            + " FROM TuitionFeeSessionEntity s, TuitionFeeEntity f, AttendanceEntity a"
            + " WHERE f.id = s.feeId AND a.id = s.attendanceId"
            + " AND f.isDeleted = 0 AND f.status <> 'CANCELLED'"
            + " AND a.classId = :classId AND a.isDeleted = 0 AND a.status IN :statuses"
            + " AND a.attendanceDate BETWEEN :fromDate AND :toDate"
            + " ORDER BY s.sessionDate, s.attendanceId")
    List<FeeSessionConflictRow> findActiveConflictsInClassRange(@Param("classId") Long classId,
                                                                @Param("fromDate") LocalDate fromDate,
                                                                @Param("toDate") LocalDate toDate,
                                                                @Param("statuses") Collection<String> statuses);

    /** Ảnh chụp ngày trên phiếu, tăng dần. */
    List<TuitionFeeSessionEntity> findByFeeIdOrderBySessionDateAscIdAsc(Long feeId);

    /** Giải phóng các ngày của phiếu (hủy / xóa mềm phiếu). Gọi trong transaction của thao tác hủy / xóa. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("DELETE FROM TuitionFeeSessionEntity s WHERE s.feeId = :feeId")
    int deleteByFeeId(@Param("feeId") Long feeId);
}
