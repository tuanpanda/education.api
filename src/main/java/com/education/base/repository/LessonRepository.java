package com.education.base.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.education.base.entity.LessonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Bài học (GĐ3). Hợp đồng Luồng 0 GĐ3 chỉ khai báo truy vấn tối thiểu; Bot 1 thêm truy vấn nghiệp vụ. */
public interface LessonRepository extends JpaRepository<LessonEntity, Long>, JpaSpecificationExecutor<LessonEntity> {

    Optional<LessonEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    List<LessonEntity> findBySectionIdInAndIsDeletedOrderBySortOrderAscIdAsc(Collection<Long> sectionIds, Integer isDeleted);

    // ---- GĐ3 Bot 1 ----
    long countBySectionIdAndIsDeleted(Long sectionId, Integer isDeleted);

    List<LessonEntity> findByCourseIdAndIsDeleted(Long courseId, Integer isDeleted);

    List<LessonEntity> findByClassIdAndIsDeleted(Long classId, Integer isDeleted);

    /** [courseId, tổng bài, bài PUBLISHED] cho danh sách khoá (gộp, không gọi từng dòng). */
    @Query("""
            SELECT l.courseId, COUNT(l), SUM(CASE WHEN l.status = 'PUBLISHED' THEN 1 ELSE 0 END)
              FROM LessonEntity l
             WHERE l.courseId IN :courseIds
               AND l.isDeleted = 0
             GROUP BY l.courseId
            """)
    List<Object[]> countByCourseIds(@Param("courseIds") Collection<Long> courseIds);
}
