package com.education.base.repository;

import com.education.base.entity.ClassEntity;
import com.education.base.repository.custom.ClassRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Hybrid repository của {@code EDU_CLASSES}: CRUD JPA + tìm kiếm phân trang qua Procedure.
 */
public interface ClassRepository extends JpaRepository<ClassEntity, Long>, ClassRepositoryCustom {

    Optional<ClassEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByClassCode(String classCode);

    Optional<ClassEntity> findByClassCodeAndIsDeleted(String classCode, Integer isDeleted);

    List<ClassEntity> findByClassCodeInAndIsDeleted(Collection<String> classCodes, Integer isDeleted);

    @Query("""
            SELECT c FROM ClassEntity c
             WHERE c.isDeleted = :deleted
               AND UPPER(c.classCode) IN :codes
            """)
    List<ClassEntity> findByClassCodesUpperAndIsDeleted(
            @Param("codes") Collection<String> codes,
            @Param("deleted") Integer deleted);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
              FROM ClassEntity c
             WHERE c.classCode = :classCode
               AND c.isDeleted = 0
            """)
    boolean existsByClassCodeAndIsDeletedFalse(@Param("classCode") String classCode);

    @Query("""
            SELECT c FROM ClassEntity c
             WHERE c.status = :status
               AND c.isDeleted = 0
             ORDER BY c.createdAt DESC
            """)
    List<ClassEntity> findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(@Param("status") String status);

    List<ClassEntity> findByStatusInAndIsDeletedOrderByCreatedAtDesc(
            Collection<String> statuses, Integer isDeleted);
}
