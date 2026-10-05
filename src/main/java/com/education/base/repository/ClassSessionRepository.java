package com.education.base.repository;

import com.education.base.entity.ClassSessionEntity;
import com.education.base.repository.custom.TimetableRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Hybrid repository of {@code EDU_CLASS_SESSIONS}: JPA CRUD + timetable via Procedure.
 */
public interface ClassSessionRepository extends JpaRepository<ClassSessionEntity, Long>, TimetableRepositoryCustom {

    Optional<ClassSessionEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByClassIdAndScheduleIdAndSessionDateAndIsDeleted(
            Long classId, Long scheduleId, LocalDate sessionDate, Integer isDeleted);

    List<ClassSessionEntity> findByClassIdAndSessionDateAndIsDeleted(
            Long classId, LocalDate sessionDate, Integer isDeleted);

    /** Whether teacher has any non-excluded session for the class. */
    boolean existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(
            Long classId, Long teacherId, Integer isDeleted, String excludedStatus);

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

    /**
     * Upcoming {@code SCHEDULED} sessions for the given classes (from {@code fromDate} onward),
     * with class + teacher fetched. Caller further filters by time-of-day.
     */
    @Query("""
            select s from ClassSessionEntity s
              left join fetch s.clazz c
              left join fetch s.teacher t
             where s.isDeleted = 0
               and s.status = 'SCHEDULED'
               and s.classId in :classIds
               and s.sessionDate >= :fromDate
             order by s.sessionDate asc, s.startTime asc, s.id asc
            """)
    List<ClassSessionEntity> findUpcomingScheduledSessions(
            @Param("classIds") Collection<Long> classIds,
            @Param("fromDate") LocalDate fromDate);

    /** ID lớp mà giảng viên có ít nhất một buổi chưa xóa và không ở trạng thái loại trừ. */
    @Query("""
            SELECT DISTINCT s.classId FROM ClassSessionEntity s
             WHERE s.teacherId = :teacherId
               AND s.isDeleted = :deleted
               AND s.status <> :excludedStatus
            """)
    List<Long> findDistinctClassIdsByTeacherIdAndIsDeletedAndStatusNot(
            @Param("teacherId") Long teacherId,
            @Param("deleted") Integer deleted,
            @Param("excludedStatus") String excludedStatus);
}
