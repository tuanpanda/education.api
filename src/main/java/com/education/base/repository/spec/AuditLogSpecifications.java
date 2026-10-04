package com.education.base.repository.spec;

import com.education.base.dto.request.AuditLogFilterRequest;
import com.education.base.entity.AuditLogEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Điều kiện tìm kiếm {@code SYS_AUDIT_LOGS} cho {@code JpaSpecificationExecutor}.
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLogEntity> fromFilter(AuditLogFilterRequest filter) {
        AuditLogFilterRequest criteria = filter == null ? new AuditLogFilterRequest() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.getFromDate() != null) {
                LocalDateTime from = criteria.getFromDate().atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventTime"), from));
            }
            if (criteria.getToDate() != null) {
                LocalDateTime toExclusive = criteria.getToDate().plusDays(1).atStartOfDay();
                predicates.add(cb.lessThan(root.get("eventTime"), toExclusive));
            }
            if (criteria.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), criteria.getUserId()));
            }
            if (hasText(criteria.getUsername())) {
                predicates.add(cb.like(cb.lower(root.get("username")), contains(criteria.getUsername()), '\\'));
            }
            if (hasText(criteria.getUserType())) {
                predicates.add(cb.equal(root.get("userType"), upper(criteria.getUserType())));
            }
            if (hasText(criteria.getAction())) {
                predicates.add(cb.equal(root.get("action"), upper(criteria.getAction())));
            }
            if (hasText(criteria.getResourceType())) {
                predicates.add(cb.equal(root.get("resourceType"), upper(criteria.getResourceType())));
            }
            if (hasText(criteria.getResourceId())) {
                predicates.add(cb.equal(root.get("resourceId"), criteria.getResourceId().trim()));
            }
            if (hasText(criteria.getResult())) {
                predicates.add(cb.equal(root.get("result"), upper(criteria.getResult())));
            }
            if (hasText(criteria.getIp())) {
                predicates.add(cb.like(root.get("ip"), escape(criteria.getIp().trim()) + "%", '\\'));
            }
            if (hasText(criteria.getKeyword())) {
                predicates.add(cb.like(cb.lower(root.get("detail")), contains(criteria.getKeyword()), '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String upper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    static String contains(String raw) {
        return "%" + escape(raw.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
