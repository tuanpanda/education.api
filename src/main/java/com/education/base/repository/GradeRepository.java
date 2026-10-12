package com.education.base.repository;

import com.education.base.entity.GradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code EDU_GRADES}.
 */
public interface GradeRepository extends JpaRepository<GradeEntity, Long> {

    List<GradeEntity> findByClassIdAndStudentIdAndIsDeleted(Long classId, Long studentId, Integer isDeleted);

    List<GradeEntity> findByClassIdAndIsDeleted(Long classId, Integer isDeleted);

    List<GradeEntity> findByStudentIdAndIsDeleted(Long studentId, Integer isDeleted);

    Optional<GradeEntity> findByClassIdAndStudentIdAndGradeTypeAndIsDeleted(
            Long classId, Long studentId, String gradeType, Integer isDeleted);

    Optional<GradeEntity> findByClassIdAndStudentIdAndGradeType(Long classId, Long studentId, String gradeType);

    // ---- GĐ2 đồng bộ sổ điểm (V20_4) ----
    List<GradeEntity> findByAssignmentIdAndStudentIdAndIsDeleted(Long assignmentId, Long studentId, Integer isDeleted);

    List<GradeEntity> findByAssignmentIdAndIsDeleted(Long assignmentId, Integer isDeleted);

    List<GradeEntity> findByClassIdAndStudentIdAndGradeTypeOrderByIdAsc(Long classId, Long studentId, String gradeType);
}
