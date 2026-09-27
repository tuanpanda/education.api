package com.education.base.repository;

import com.education.base.entity.ClassScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository JPA của {@code EDU_CLASS_SCHEDULES}.
 */
public interface ClassScheduleRepository extends JpaRepository<ClassScheduleEntity, Long> {

    List<ClassScheduleEntity> findByClassIdAndIsDeletedOrderByDayOfWeekAscStartTimeAsc(
            Long classId, Integer isDeleted);

    List<ClassScheduleEntity> findByClassIdAndStatusAndIsDeletedOrderByDayOfWeekAscStartTimeAsc(
            Long classId, String status, Integer isDeleted);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
              FROM ClassScheduleEntity s
             WHERE s.isDeleted = 0
               AND s.status = 'ACTIVE'
               AND s.dayOfWeek = :dayOfWeek
               AND UPPER(s.roomName) = UPPER(:roomName)
               AND s.classId <> :classId
               AND s.startTime < :endTime
               AND s.endTime > :startTime
            """)
    boolean existsRoomConflict(
            @Param("classId") Long classId,
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("roomName") String roomName,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
              FROM ClassScheduleEntity s
             WHERE s.isDeleted = 0
               AND s.status = 'ACTIVE'
               AND s.dayOfWeek = :dayOfWeek
               AND s.teacherId = :teacherId
               AND s.classId <> :classId
               AND s.startTime < :endTime
               AND s.endTime > :startTime
            """)
    boolean existsTeacherConflict(
            @Param("classId") Long classId,
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("teacherId") Long teacherId,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);
}
