package com.education.base.repository;

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
}
