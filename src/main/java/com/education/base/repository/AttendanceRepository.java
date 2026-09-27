package com.education.base.repository;

import com.education.base.entity.AttendanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
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
}
