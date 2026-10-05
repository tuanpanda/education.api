package com.education.base.repository;

import com.education.base.entity.ClassStudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Ghi danh đang học ({@code ENROLLED}, chưa xóa) của học sinh kèm lớp + giáo viên (chưa xóa),
     * lớp mới nhất trước — dùng cho cổng học sinh.
     */
    @Query("""
            select cs from ClassStudentEntity cs
              join fetch cs.clazz c
              left join fetch c.teacher t
             where cs.studentId = :studentId
               and cs.isDeleted = 0
               and cs.status = 'ENROLLED'
               and c.isDeleted = 0
             order by cs.enrolledAt desc, cs.id desc
            """)
    List<ClassStudentEntity> findActiveEnrollmentsWithClass(
            @Param("studentId") Long studentId);
}
