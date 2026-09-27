package com.education.base.repository;

import com.education.base.entity.ClassStudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code EDU_CLASS_STUDENTS}.
 */
public interface ClassStudentRepository extends JpaRepository<ClassStudentEntity, Long> {

    Optional<ClassStudentEntity> findByClassIdAndStudentId(Long classId, Long studentId);

    Optional<ClassStudentEntity> findByClassIdAndStudentIdAndIsDeleted(Long classId, Long studentId, Integer isDeleted);

    boolean existsByClassIdAndStudentId(Long classId, Long studentId);

    List<ClassStudentEntity> findByClassIdAndIsDeleted(Long classId, Integer isDeleted);

    List<ClassStudentEntity> findByStudentIdAndIsDeleted(Long studentId, Integer isDeleted);

    Optional<ClassStudentEntity> findFirstByStudentIdAndIsDeletedAndStatusOrderByEnrolledAtDesc(
            Long studentId, Integer isDeleted, String status);

    long countByClassIdAndStatusAndIsDeleted(Long classId, String status, Integer isDeleted);
}
