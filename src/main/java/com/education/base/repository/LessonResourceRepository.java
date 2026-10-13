package com.education.base.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.education.base.entity.LessonResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Tài nguyên bài học (GĐ3). Hợp đồng Luồng 0 GĐ3 chỉ khai báo truy vấn tối thiểu; Bot 1 thêm truy vấn nghiệp vụ. */
public interface LessonResourceRepository extends JpaRepository<LessonResourceEntity, Long>, JpaSpecificationExecutor<LessonResourceEntity> {

    Optional<LessonResourceEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    List<LessonResourceEntity> findByLessonIdAndIsDeletedOrderBySortOrderAscIdAsc(Long lessonId, Integer isDeleted);

    // ---- GĐ3 Bot 1 ----
    long countByLessonIdAndIsDeleted(Long lessonId, Integer isDeleted);

    List<LessonResourceEntity> findByLessonIdInAndIsDeletedOrderBySortOrderAscIdAsc(Collection<Long> lessonIds,
                                                                                   Integer isDeleted);

    List<LessonResourceEntity> findByFileIdAndIsDeleted(Long fileId, Integer isDeleted);

    /** Còn tài nguyên chưa xoá khác (ngoài {@code excludeId}) dùng chung FILE_ID không. */
    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
              FROM LessonResourceEntity r
             WHERE r.fileId = :fileId
               AND r.isDeleted = 0
               AND r.id <> :excludeId
            """)
    boolean existsOtherLiveByFileId(@Param("fileId") Long fileId, @Param("excludeId") Long excludeId);
}
