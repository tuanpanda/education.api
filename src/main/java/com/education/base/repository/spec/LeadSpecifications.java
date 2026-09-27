package com.education.base.repository.spec;

import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.LeadFilterRequest;
import com.education.base.entity.LeadEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Điều kiện tìm kiếm {@code EDU_LEADS} cho {@code JpaSpecificationExecutor}.
 */
public final class LeadSpecifications {

    private LeadSpecifications() {
    }

    public static Specification<LeadEntity> fromFilter(LeadFilterRequest filter) {
        LeadFilterRequest criteria = filter == null ? new LeadFilterRequest() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));

            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                String like = contains(criteria.getKeyword());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("leadCode")), like, '\\'),
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like, '\\')
                ));
            }
            if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus().trim()));
            }
            if (criteria.getSource() != null && !criteria.getSource().isBlank()) {
                predicates.add(cb.equal(root.get("source"), criteria.getSource().trim()));
            }
            if (criteria.getAssignedToId() != null) {
                predicates.add(cb.equal(root.get("assignedToId"), criteria.getAssignedToId()));
            }
            if (criteria.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.getFromDate().atStartOfDay()));
            }
            if (criteria.getToDate() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), criteria.getToDate().plusDays(1).atStartOfDay()));
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
