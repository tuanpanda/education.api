package com.education.base.repository;

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
}
