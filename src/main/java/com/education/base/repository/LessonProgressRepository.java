package com.education.base.repository;

import com.education.base.entity.LessonProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Tiến độ học (GĐ3). Hợp đồng Luồng 0 GĐ3 chỉ khai báo truy vấn tối thiểu; Bot 1 thêm truy vấn nghiệp vụ. */
public interface LessonProgressRepository extends JpaRepository<LessonProgressEntity, Long>, JpaSpecificationExecutor<LessonProgressEntity> {

    Optional<LessonProgressEntity> findByLessonIdAndStudentId(Long lessonId, Long studentId);

    List<LessonProgressEntity> findByStudentIdAndLessonIdIn(Long studentId, Collection<Long> lessonIds);

    List<LessonProgressEntity> findByLessonIdIn(Collection<Long> lessonIds);
}
