package com.education.base.repository;

import com.education.base.entity.ClassSessionEntity;
import com.education.base.repository.custom.TimetableRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Hybrid repository của {@code EDU_CLASS_SESSIONS}: CRUD JPA + tra cứu TKB qua Procedure.
 */
public interface ClassSessionRepository extends JpaRepository<ClassSessionEntity, Long>, TimetableRepositoryCustom {

    Optional<ClassSessionEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByClassIdAndScheduleIdAndSessionDateAndIsDeleted(
            Long classId, Long scheduleId, LocalDate sessionDate, Integer isDeleted);

    List<ClassSessionEntity> findByClassIdAndSessionDateAndIsDeleted(
            Long classId, LocalDate sessionDate, Integer isDeleted);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
              FROM ClassSessionEntity s
             WHERE s.isDeleted = 0
               AND s.status <> 'CANCELLED'
               AND s.sessionDate = :sessionDate
               AND UPPER(s.roomName) = UPPER(:roomName)
               AND s.startTime < :endTime
               AND s.endTime > :startTime
               AND (:excludeId IS NULL OR s.id <> :excludeId)
            """)
    boolean existsRoomConflict(
            @Param("sessionDate") LocalDate sessionDate,
            @Param("roomName") String roomName,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("excludeId") Long excludeId);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
              FROM ClassSessionEntity s
             WHERE s.isDeleted = 0
               AND s.status <> 'CANCELLED'
               AND s.sessionDate = :sessionDate
               AND s.teacherId = :teacherId
               AND s.startTime < :endTime
               AND s.endTime > :startTime
               AND (:excludeId IS NULL OR s.id <> :excludeId)
            """)
    boolean existsTeacherConflict(
            @Param("sessionDate") LocalDate sessionDate,
            @Param("teacherId") Long teacherId,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("excludeId") Long excludeId);
}
