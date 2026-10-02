package com.education.base.repository.spec;

import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.StudentDiscountFilterRequest;
import com.education.base.entity.StudentDiscountEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Điều kiện tìm kiếm {@code FIN_STUDENT_DISCOUNTS} cho {@code JpaSpecificationExecutor}.
 */
public final class StudentDiscountSpecifications {

    private StudentDiscountSpecifications() {
    }

    public static Specification<StudentDiscountEntity> fromFilter(StudentDiscountFilterRequest filter) {
        StudentDiscountFilterRequest criteria = filter == null ? new StudentDiscountFilterRequest() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            Join<Object, Object> student = root.join("student", JoinType.INNER);
            predicates.add(cb.equal(student.get("isDeleted"), PersistenceFlags.NOT_DELETED));

            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                String like = contains(criteria.getKeyword());
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(student.get("studentCode"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(student.get("fullName"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("reason"), "")), like, '\\')
                ));
            }
            if (criteria.getStudentId() != null) {
                predicates.add(cb.equal(root.get("studentId"), criteria.getStudentId()));
            }
            if (criteria.getClassId() != null) {
                predicates.add(cb.equal(root.get("classId"), criteria.getClassId()));
            }
            if (criteria.getDiscountType() != null && !criteria.getDiscountType().isBlank()) {
                predicates.add(cb.equal(root.get("discountType"), criteria.getDiscountType().trim()));
            }
            if (criteria.getActiveOn() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("validFrom"), criteria.getActiveOn()));
                predicates.add(cb.or(
                        cb.isNull(root.get("validTo")),
                        cb.greaterThanOrEqualTo(root.get("validTo"), criteria.getActiveOn())));
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
