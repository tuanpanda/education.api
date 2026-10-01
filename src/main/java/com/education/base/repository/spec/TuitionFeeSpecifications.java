package com.education.base.repository.spec;

import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.entity.TuitionFeeEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Điều kiện tìm kiếm {@code FIN_TUITION_FEES} cho {@code JpaSpecificationExecutor}.
 */
public final class TuitionFeeSpecifications {

    private TuitionFeeSpecifications() {
    }

    public static Specification<TuitionFeeEntity> fromFilter(TuitionFeeFilterRequest filter) {
        TuitionFeeFilterRequest criteria = filter == null ? new TuitionFeeFilterRequest() : filter;
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            Join<Object, Object> student = root.join("student", JoinType.INNER);
            predicates.add(cb.equal(student.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            // B8: không ẩn khoản phí (công nợ) của học sinh đã nghỉ / tốt nghiệp / tạm dừng; lọc theo trạng thái
            // học sinh chỉ khi người dùng chọn (mặc định: mọi trạng thái).
            if (criteria.getStudentStatus() != null && !criteria.getStudentStatus().isBlank()) {
                predicates.add(cb.equal(student.get("status"), criteria.getStudentStatus().trim()));
            }

            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                String like = contains(criteria.getKeyword());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("feeCode")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(student.get("studentCode"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(student.get("fullName"), "")), like, '\\')
                ));
            }
            if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus().trim()));
            }
            if (criteria.getStudentId() != null) {
                predicates.add(cb.equal(root.get("studentId"), criteria.getStudentId()));
            }
            if (criteria.getClassId() != null) {
                predicates.add(cb.equal(root.get("classId"), criteria.getClassId()));
            }
            if (criteria.getDueFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), criteria.getDueFromDate()));
            }
            if (criteria.getDueToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), criteria.getDueToDate()));
            }
            if (Boolean.TRUE.equals(criteria.getOverdueOnly())) {
                predicates.add(cb.lessThan(root.get("dueDate"), LocalDate.now()));
                predicates.add(root.get("status").in("UNPAID", "PARTIAL", "OVERDUE"));
                predicates.add(cb.greaterThan(
                        cb.diff(cb.diff(root.get("totalAmount"), root.get("discountAmount")), root.get("paidAmount")),
                        BigDecimal.ZERO));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String contains(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
