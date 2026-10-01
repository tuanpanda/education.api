package com.education.base.repository.spec;

import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.PaymentTransactionFilterRequest;
import com.education.base.entity.PaymentTransactionEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Điều kiện tìm kiếm {@code FIN_PAYMENT_TRANSACTIONS} cho {@code JpaSpecificationExecutor}.
 * Luôn loại bỏ dòng {@code IS_DELETED = 1}; lọc theo học sinh / lớp / mã khoản phí qua khoản học phí.
 */
public final class PaymentTransactionSpecifications {

    private PaymentTransactionSpecifications() {
    }

    public static Specification<PaymentTransactionEntity> fromFilter(PaymentTransactionFilterRequest filter) {
        PaymentTransactionFilterRequest criteria = filter == null ? new PaymentTransactionFilterRequest() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));

            if (criteria.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"),
                        criteria.getFromDate().atStartOfDay()));
            }
            if (criteria.getToDate() != null) {
                predicates.add(cb.lessThan(root.get("paymentDate"),
                        criteria.getToDate().plusDays(1).atStartOfDay()));
            }
            if (hasText(criteria.getPaymentMethod())) {
                predicates.add(cb.equal(root.get("paymentMethod"), criteria.getPaymentMethod().trim()));
            }
            if (hasText(criteria.getStatus())) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus().trim()));
            }
            if (hasText(criteria.getTransactionType())) {
                predicates.add(cb.equal(root.get("transactionType"), criteria.getTransactionType().trim()));
            }
            if (hasText(criteria.getReceiptNo())) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("receiptNo"), "")),
                        contains(criteria.getReceiptNo()), '\\'));
            }

            boolean needFee = criteria.getStudentId() != null || criteria.getClassId() != null
                    || hasText(criteria.getFeeCode()) || hasText(criteria.getKeyword());
            if (needFee) {
                Join<Object, Object> fee = root.join("tuitionFee", JoinType.INNER);
                if (criteria.getStudentId() != null) {
                    predicates.add(cb.equal(fee.get("studentId"), criteria.getStudentId()));
                }
                if (criteria.getClassId() != null) {
                    predicates.add(cb.equal(fee.get("classId"), criteria.getClassId()));
                }
                if (hasText(criteria.getFeeCode())) {
                    predicates.add(cb.like(cb.lower(fee.get("feeCode")), contains(criteria.getFeeCode()), '\\'));
                }
                if (hasText(criteria.getKeyword())) {
                    Join<Object, Object> student = fee.join("student", JoinType.LEFT);
                    String like = contains(criteria.getKeyword());
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("transactionCode")), like, '\\'),
                            cb.like(cb.lower(cb.coalesce(root.get("payerName"), "")), like, '\\'),
                            cb.like(cb.lower(cb.coalesce(student.get("studentCode"), "")), like, '\\'),
                            cb.like(cb.lower(cb.coalesce(student.get("fullName"), "")), like, '\\')
                    ));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String contains(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
