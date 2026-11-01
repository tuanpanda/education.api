package com.education.base.repository;

import com.education.base.entity.TuitionFeeEntity;
import com.education.base.repository.custom.TuitionFeeRepositoryCustom;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Hybrid repository của {@code FIN_TUITION_FEES}: CRUD JPA + chi tiết qua Procedure.
 */
public interface TuitionFeeRepository extends JpaRepository<TuitionFeeEntity, Long>,
        TuitionFeeRepositoryCustom, JpaSpecificationExecutor<TuitionFeeEntity> {

    Optional<TuitionFeeEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * Nạp khoản học phí và khóa dòng ({@code SELECT ... FOR UPDATE WAIT n}) trong transaction hiện tại,
     * dùng khi ghi nhận thanh toán để hai request đồng thời không cùng cộng {@code PAID_AMOUNT}.
     * Phải gọi bên trong {@code @Transactional}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT f FROM TuitionFeeEntity f WHERE f.id = :id AND f.isDeleted = :isDeleted")
    Optional<TuitionFeeEntity> findByIdAndIsDeletedForUpdate(@Param("id") Long id,
                                                            @Param("isDeleted") Integer isDeleted);

    boolean existsByFeeCode(String feeCode);

    List<TuitionFeeEntity> findByStudentIdAndIsDeleted(Long studentId, Integer isDeleted);

    Optional<TuitionFeeEntity> findByStudentIdAndClassIdAndFeeYearAndFeeMonthAndIsDeleted(
            Long studentId, Long classId, Integer feeYear, Integer feeMonth, Integer isDeleted);
}
