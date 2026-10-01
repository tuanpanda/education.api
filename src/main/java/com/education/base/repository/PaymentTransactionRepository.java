package com.education.base.repository;

import com.education.base.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code FIN_PAYMENT_TRANSACTIONS}.
 */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long>,
        JpaSpecificationExecutor<PaymentTransactionEntity> {

    Optional<PaymentTransactionEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByTransactionCode(String transactionCode);

    List<PaymentTransactionEntity> findByTuitionFeeIdAndIsDeleted(Long tuitionFeeId, Integer isDeleted);

    boolean existsByBankReferenceNo(String bankReferenceNo);

    /**
     * Chỉ lấy ID khoản học phí của giao dịch (không nạp entity vào persistence context), để khóa khoản phí
     * trước rồi mới đọc trạng thái giao dịch - tránh đọc dữ liệu cũ khi hủy / hoàn tiền đồng thời.
     */
    @Query("SELECT t.tuitionFeeId FROM PaymentTransactionEntity t WHERE t.id = :id AND t.isDeleted = :isDeleted")
    Optional<Long> findTuitionFeeIdByIdAndIsDeleted(@Param("id") Long id, @Param("isDeleted") Integer isDeleted);

    /** Các dòng hoàn tiền của một giao dịch thu, mới nhất trước. */
    List<PaymentTransactionEntity> findByRefTransactionIdAndIsDeletedOrderByPaymentDateDescIdDesc(
            Long refTransactionId, Integer isDeleted);

    /** Các dòng hoàn tiền của nhiều giao dịch thu (trang kết quả tra cứu, tránh N+1). */
    List<PaymentTransactionEntity> findByRefTransactionIdInAndIsDeleted(Collection<Long> refTransactionIds,
                                                                     Integer isDeleted);
}
