package com.education.base.repository;

import com.education.base.entity.StudentDiscountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code FIN_STUDENT_DISCOUNTS} (V14_1).
 */
public interface StudentDiscountRepository extends JpaRepository<StudentDiscountEntity, Long>,
        JpaSpecificationExecutor<StudentDiscountEntity> {

    Optional<StudentDiscountEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * Miễn giảm còn hiệu lực trong kỳ {@code [periodStart, periodEnd]} (khoảng hiệu lực giao với kỳ thu) của các
     * học sinh {@code studentIds}, áp cho lớp {@code classId} hoặc cho mọi lớp ({@code CLASS_ID IS NULL}).
     */
    @Query("SELECT d FROM StudentDiscountEntity d"
            + " WHERE d.isDeleted = 0"
            + " AND d.studentId IN :studentIds"
            + " AND (d.classId IS NULL OR d.classId = :classId)"
            + " AND d.validFrom <= :periodEnd"
            + " AND (d.validTo IS NULL OR d.validTo >= :periodStart)"
            + " ORDER BY d.id")
    List<StudentDiscountEntity> findApplicable(@Param("studentIds") Collection<Long> studentIds,
                                               @Param("classId") Long classId,
                                               @Param("periodStart") LocalDate periodStart,
                                               @Param("periodEnd") LocalDate periodEnd);
}
