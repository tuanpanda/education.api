package com.education.base.repository;

import com.education.base.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code FIN_PAYMENT_TRANSACTIONS}.
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {

    Optional<PaymentTransactionEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByTransactionCode(String transactionCode);

    List<PaymentTransactionEntity> findByTuitionFeeIdAndIsDeleted(Long tuitionFeeId, Integer isDeleted);

    boolean existsByBankReferenceNo(String bankReferenceNo);
}
