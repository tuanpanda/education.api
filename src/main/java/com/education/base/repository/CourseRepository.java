package com.education.base.repository;

import com.education.base.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Khoá học mẫu (GĐ3). Hợp đồng Luồng 0 GĐ3 chỉ khai báo truy vấn tối thiểu; Bot 1 thêm truy vấn nghiệp vụ. */
public interface CourseRepository extends JpaRepository<CourseEntity, Long>, JpaSpecificationExecutor<CourseEntity> {

    Optional<CourseEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByCourseCodeIgnoreCaseAndIsDeleted(String courseCode, Integer isDeleted);
}
