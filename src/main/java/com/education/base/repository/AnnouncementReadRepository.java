package com.education.base.repository;

import com.education.base.entity.AnnouncementReadEntity;
import com.education.base.entity.AnnouncementReadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

/**
 * Repository JPA của {@code EDU_ANNOUNCEMENT_READS}.
 */
public interface AnnouncementReadRepository extends JpaRepository<AnnouncementReadEntity, AnnouncementReadId> {

    boolean existsByAnnouncementIdAndUserId(Long announcementId, Long userId);

    @Query("""
            select r.announcementId from AnnouncementReadEntity r
             where r.userId = :userId and r.announcementId in :announcementIds
            """)
    Set<Long> findReadAnnouncementIds(
            @Param("userId") Long userId,
            @Param("announcementIds") Collection<Long> announcementIds);
}
