package com.education.base.repository;

import com.education.base.entity.AttendanceEntity;
import com.education.base.repository.projection.BillableAttendanceRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code EDU_ATTENDANCE}.
 */
public interface AttendanceRepository extends JpaRepository<AttendanceEntity, Long> {

    Optional<AttendanceEntity> findByClassIdAndStudentIdAndAttendanceDateAndIsDeleted(
            Long classId, Long studentId, LocalDate attendanceDate, Integer isDeleted);

    List<AttendanceEntity> findByClassIdAndAttendanceDateAndIsDeleted(
            Long classId, LocalDate attendanceDate, Integer isDeleted);

    List<AttendanceEntity> findByClassIdAndIsDeleted(Long classId, Integer isDeleted);

    List<AttendanceEntity> findByClassIdAndAttendanceDateBetweenAndIsDeleted(
            Long classId, LocalDate fromDate, LocalDate toDate, Integer isDeleted);

    List<AttendanceEntity> findByClassIdAndStudentIdAndIsDeleted(Long classId, Long studentId, Integer isDeleted);

    List<AttendanceEntity> findByStudentIdAndIsDeleted(Long studentId, Integer isDeleted);

    long countByStudentIdAndIsDeleted(Long studentId, Integer isDeleted);

    long countByStudentIdAndIsDeletedAndStatusIn(
            Long studentId, Integer isDeleted, Collection<String> statuses);

    List<AttendanceEntity> findByClassIdInAndAttendanceDateBetweenAndIsDeleted(
            Collection<Long> classIds, LocalDate fromDate, LocalDate toDate, Integer isDeleted);

    /**
     * Buổi tính phí của lớp trong khoảng ngày (cả hai đầu): {@code IS_DELETED = 0}, {@code STATUS IN :statuses}.
     * Trả cả học sinh đã nghỉ lớp (contract fee-range-api v1.3: vẫn thu buổi đã học).
     */
    @Query("SELECT new com.education.base.repository.projection.BillableAttendanceRow("
            + "a.id, a.studentId, a.attendanceDate) FROM AttendanceEntity a"
            + " WHERE a.classId = :classId AND a.isDeleted = 0 AND a.status IN :statuses"
            + " AND a.attendanceDate BETWEEN :fromDate AND :toDate"
            + " ORDER BY a.studentId, a.attendanceDate, a.id")
    List<BillableAttendanceRow> findBillableInClassRange(@Param("classId") Long classId,
                                                         @Param("fromDate") LocalDate fromDate,
                                                         @Param("toDate") LocalDate toDate,
                                                         @Param("statuses") Collection<String> statuses);
}
