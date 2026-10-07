package com.education.base.repository.spec;

import com.education.base.dto.request.FileFilterRequest;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.UserEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Điều kiện tìm kiếm {@code SYS_ATTACHED_FILES} cho màn "Quản lý file" (Luồng B), theo mẫu
 * {@link AuditLogSpecifications}.
 */
public final class FileSpecifications {

    /** Oracle giới hạn 1000 phần tử trong một IN-list. */
    static final int IN_CHUNK = 1000;

    private FileSpecifications() {
    }

    public static Specification<FileEntity> fromFilter(FileFilterRequest filter) {
        return fromFilter(filter, null);
    }

    /**
     * @param references khi khác {@code null}: chỉ giữ file có {@code (MODULE_NAME, REFERENCE_ID)} thuộc map
     *                   (dùng cho lọc {@code refState} ACTIVE / DELETED / NOT_FOUND - service tính trước bằng
     *                   descriptor). Map rỗng = không khớp dòng nào.
     */
    public static Specification<FileEntity> fromFilter(FileFilterRequest filter,
                                                       Map<String, ? extends Collection<Long>> references) {
        FileFilterRequest criteria = filter == null ? new FileFilterRequest() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String status = criteria.getStatus();
            if (hasText(status) && !FileFilterRequest.STATUS_ALL.equalsIgnoreCase(status.trim())) {
                predicates.add(cb.equal(root.get("status"), upper(status)));
            }
            if (hasText(criteria.getModule())) {
                predicates.add(cb.equal(root.get("moduleName"), upper(criteria.getModule())));
            }
            if (criteria.getReferenceId() != null) {
                predicates.add(cb.equal(root.get("referenceId"), criteria.getReferenceId()));
            }
            if (criteria.getUploadedByUserId() != null) {
                predicates.add(cb.equal(root.get("uploadedByUserId"), criteria.getUploadedByUserId()));
            }
            if (criteria.getOwnerStudentId() != null) {
                predicates.add(cb.equal(root.get("ownerStudentId"), criteria.getOwnerStudentId()));
            }
            if (hasText(criteria.getExt())) {
                predicates.add(cb.equal(root.get("fileExt"), criteria.getExt().trim().toLowerCase(Locale.ROOT)));
            }
            if (hasText(criteria.getContentType())) {
                predicates.add(cb.like(cb.lower(root.get("contentType")),
                        escape(criteria.getContentType().trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (hasText(criteria.getScanStatus())) {
                predicates.add(cb.equal(root.get("scanStatus"), upper(criteria.getScanStatus())));
            }
            if (criteria.getCreatedFrom() != null) {
                LocalDateTime from = criteria.getCreatedFrom().atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (criteria.getCreatedTo() != null) {
                LocalDateTime toExclusive = criteria.getCreatedTo().plusDays(1).atStartOfDay();
                predicates.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            if (criteria.getMinSize() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fileSize"), criteria.getMinSize()));
            }
            if (criteria.getMaxSize() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fileSize"), criteria.getMaxSize()));
            }
            if ("NONE".equalsIgnoreCase(trimOrNull(criteria.getRefState()))) {
                predicates.add(cb.isNull(root.get("referenceId")));
            } else if (references != null) {
                List<Predicate> any = new ArrayList<>();
                references.forEach((module, ids) -> {
                    List<Long> list = ids == null ? List.of() : List.copyOf(ids);
                    for (int i = 0; i < list.size(); i += IN_CHUNK) {
                        any.add(cb.and(cb.equal(root.get("moduleName"), module),
                                root.get("referenceId").in(list.subList(i, Math.min(list.size(), i + IN_CHUNK)))));
                    }
                });
                predicates.add(any.isEmpty() ? cb.disjunction() : cb.or(any.toArray(Predicate[]::new)));
            }
            if (hasText(criteria.getQ())) {
                String pattern = contains(criteria.getQ());
                Subquery<Long> students = query.subquery(Long.class);
                var s = students.from(StudentEntity.class);
                students.select(s.get("id")).where(cb.or(
                        cb.like(cb.lower(s.get("studentCode")), pattern, '\\'),
                        cb.like(cb.lower(s.get("fullName")), pattern, '\\')));
                Subquery<Long> users = query.subquery(Long.class);
                var u = users.from(UserEntity.class);
                users.select(u.get("id")).where(cb.or(
                        cb.like(cb.lower(u.get("username")), pattern, '\\'),
                        cb.like(cb.lower(u.get("fullName")), pattern, '\\')));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("originalName")), pattern, '\\'),
                        root.get("ownerStudentId").in(students),
                        root.get("uploadedByUserId").in(users)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
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
