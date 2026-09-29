package com.education.base.repository.spec;

import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.RoleFilterRequest;
import com.education.base.entity.RoleEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Điều kiện tìm kiếm {@code SYS_ROLES} cho {@code JpaSpecificationExecutor}.
 */
public final class RoleSpecifications {

    private RoleSpecifications() {
    }

    public static Specification<RoleEntity> fromFilter(RoleFilterRequest filter) {
        RoleFilterRequest criteria = filter == null ? new RoleFilterRequest() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                String like = UserSpecifications.contains(criteria.getKeyword());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("roleCode")), like, '\\'),
                        cb.like(cb.lower(root.get("roleName")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like, '\\')
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
