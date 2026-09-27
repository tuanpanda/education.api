package com.education.base.repository;

import com.education.base.entity.TuitionFeeEntity;
import com.education.base.repository.custom.TuitionFeeRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Hybrid repository của {@code FIN_TUITION_FEES}: CRUD JPA + chi tiết qua Procedure.
 */
public interface TuitionFeeRepository extends JpaRepository<TuitionFeeEntity, Long>,
        TuitionFeeRepositoryCustom, JpaSpecificationExecutor<TuitionFeeEntity> {

    Optional<TuitionFeeEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByFeeCode(String feeCode);

    List<TuitionFeeEntity> findByStudentIdAndIsDeleted(Long studentId, Integer isDeleted);

    Optional<TuitionFeeEntity> findByStudentIdAndClassIdAndFeeYearAndFeeMonthAndIsDeleted(
            Long studentId, Long classId, Integer feeYear, Integer feeMonth, Integer isDeleted);
}
