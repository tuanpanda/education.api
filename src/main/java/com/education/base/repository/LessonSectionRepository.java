package com.education.base.repository;

import com.education.base.entity.LessonSectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Chương (GĐ3). Hợp đồng Luồng 0 GĐ3 chỉ khai báo truy vấn tối thiểu; Bot 1 thêm truy vấn nghiệp vụ. */
public interface LessonSectionRepository extends JpaRepository<LessonSectionEntity, Long>, JpaSpecificationExecutor<LessonSectionEntity> {

    Optional<LessonSectionEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    List<LessonSectionEntity> findByCourseIdAndIsDeletedOrderBySortOrderAscIdAsc(Long courseId, Integer isDeleted);

    List<LessonSectionEntity> findByClassIdAndIsDeletedOrderBySortOrderAscIdAsc(Long classId, Integer isDeleted);

    // ---- GĐ3 Bot 1 ----
    long countByCourseIdAndIsDeleted(Long courseId, Integer isDeleted);

    long countByClassIdAndIsDeleted(Long classId, Integer isDeleted);
}
