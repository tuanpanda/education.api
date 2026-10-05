package com.education.base.repository;

import com.education.base.entity.AnnouncementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code EDU_ANNOUNCEMENTS}.
 */
public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, Long>,
        JpaSpecificationExecutor<AnnouncementEntity> {

    Optional<AnnouncementEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * Thông báo PUBLISHED còn hiệu lực dành cho học sinh (audience STUDENT/ALL),
     * phạm vi ALL hoặc CLASS thuộc lớp đang ghi danh.
     */
    @Query("""
            select a from AnnouncementEntity a
             where a.isDeleted = 0
               and a.status = 'PUBLISHED'
               and a.audience in ('STUDENT', 'ALL')
               and (a.expiresAt is null or a.expiresAt > :now)
               and (
                    a.scopeType = 'ALL'
                    or (a.scopeType = 'CLASS' and a.classId in :classIds)
               )
             order by a.isPinned desc, a.publishedAt desc, a.id desc
            """)
    List<AnnouncementEntity> findVisibleForStudent(
            @Param("classIds") Collection<Long> classIds,
            @Param("now") LocalDateTime now);

    @Query("""
            select count(a) from AnnouncementEntity a
             where a.isDeleted = 0
               and a.status = 'PUBLISHED'
               and a.audience in ('STUDENT', 'ALL')
               and (a.expiresAt is null or a.expiresAt > :now)
               and (
                    a.scopeType = 'ALL'
                    or (a.scopeType = 'CLASS' and a.classId in :classIds)
               )
               and not exists (
                    select 1 from AnnouncementReadEntity r
                     where r.announcementId = a.id and r.userId = :userId
               )
            """)
    long countUnreadForStudent(
            @Param("userId") Long userId,
            @Param("classIds") Collection<Long> classIds,
            @Param("now") LocalDateTime now);
}
