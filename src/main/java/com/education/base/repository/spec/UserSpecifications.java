package com.education.base.repository.spec;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.UserFilterRequest;
import com.education.base.entity.UserEntity;
import com.education.base.entity.UserRoleEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Điều kiện tìm kiếm {@code SYS_USERS} cho {@code JpaSpecificationExecutor}.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<UserEntity> fromFilter(UserFilterRequest filter) {
        UserFilterRequest criteria = filter == null ? new UserFilterRequest() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            // Màn hình "Người dùng" chỉ quản lý tài khoản nhân viên; tài khoản học sinh có màn hình riêng (V17).
            predicates.add(cb.equal(root.get("userType"), DomainConstants.USER_TYPE_STAFF));

            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                String like = contains(criteria.getKeyword());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like, '\\'),
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like, '\\')
                ));
            }
            if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus().trim()));
            }
            if (criteria.getRoleId() != null && query != null) {
                Subquery<Long> byRole = query.subquery(Long.class);
                Root<UserRoleEntity> userRole = byRole.from(UserRoleEntity.class);
                byRole.select(userRole.get("userId"))
                        .where(cb.equal(userRole.get("roleId"), criteria.getRoleId()));
                predicates.add(root.get("id").in(byRole));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String contains(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
