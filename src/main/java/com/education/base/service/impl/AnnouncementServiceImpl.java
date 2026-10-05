package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.AnnouncementFilterRequest;
import com.education.base.dto.request.AnnouncementUpsertRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.AnnouncementDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.AnnouncementEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AnnouncementRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AnnouncementService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    public static final String NOT_FOUND = "ANNOUNCEMENT_NOT_FOUND";
    public static final String INVALID_SCOPE = "ANNOUNCEMENT_INVALID_SCOPE";
    public static final String CLASS_REQUIRED = "ANNOUNCEMENT_CLASS_REQUIRED";
    public static final String CLASS_NOT_FOUND = "ANNOUNCEMENT_CLASS_NOT_FOUND";
    public static final String CONTENT_EMPTY = "ANNOUNCEMENT_CONTENT_EMPTY";
    public static final String INVALID_STATUS = "ANNOUNCEMENT_INVALID_STATUS";
    /** Giáo viên cố tạo/sửa phạm vi ALL hoặc thao tác trên thông báo ALL / lớp không được phân công. */
    public static final String SCOPE_FORBIDDEN = "ANNOUNCEMENT_SCOPE_FORBIDDEN";

    private static final String MSG_TEACHER_ALL_FORBIDDEN =
            "Giáo viên chỉ được tạo hoặc sửa thông báo theo lớp mình dạy, không được dùng phạm vi toàn trung tâm.";
    private static final String MSG_TEACHER_MANAGE_FORBIDDEN =
            "Bạn chỉ được quản lý thông báo theo lớp mình phụ trách.";
    private static final String MSG_TEACHER_CLASS_FORBIDDEN =
            "Bạn chỉ được đăng thông báo cho lớp mình phụ trách.";

    private final AnnouncementRepository announcementRepository;
    private final ClassRepository classRepository;
    private final HtmlContentSanitizer htmlContentSanitizer;
    private final TeachingAssignmentGuard teachingAssignmentGuard;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementDto> search(AnnouncementFilterRequest filter) {
        AnnouncementFilterRequest f = filter == null ? new AnnouncementFilterRequest() : filter;
        Optional<Set<Long>> taughtOpt = teachingAssignmentGuard.taughtClassIdsIfRestricted();
        if (taughtOpt.isPresent()) {
            Set<Long> taught = taughtOpt.get();
            if (f.getClassId() != null && !taught.contains(f.getClassId())) {
                int pageNo = Math.max(f.getPageNo(), 1);
                int pageSize = Math.max(f.getPageSize(), 1);
                return PageResponse.of(List.of(), pageNo, pageSize, 0);
            }
            if (f.getScopeType() != null && !f.getScopeType().isBlank()
                    && DomainConstants.ANNOUNCEMENT_SCOPE_CLASS.equals(
                    f.getScopeType().trim().toUpperCase(Locale.ROOT))
                    && taught.isEmpty()) {
                int pageNo = Math.max(f.getPageNo(), 1);
                int pageSize = Math.max(f.getPageSize(), 1);
                return PageResponse.of(List.of(), pageNo, pageSize, 0);
            }
        }
        Specification<AnnouncementEntity> spec = buildSpec(f, taughtOpt);
        int pageNo = Math.max(f.getPageNo(), 1);
        int pageSize = Math.max(f.getPageSize(), 1);
        PageRequest pageable = PageRequest.of(
                pageNo - 1,
                pageSize,
                Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        Page<AnnouncementEntity> page = announcementRepository.findAll(spec, pageable);
        List<AnnouncementDto> content = page.getContent().stream().map(this::toDto).toList();
        return PageResponse.of(content, pageNo, pageSize, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementDto getById(Long id) {
        AnnouncementEntity entity = requireExisting(id);
        requireTeacherCanView(entity);
        return toDto(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementDto create(AnnouncementUpsertRequest request) {
        AnnouncementUpsertRequest payload = requirePayload(request);
        ScopeResolved scope = resolveScope(payload);
        requireTeacherCanWriteScope(scope);
        String content = requireSanitizedContent(payload.getContent());
        String username = SecurityUtils.currentUsername();
        AnnouncementEntity entity = AnnouncementEntity.builder()
                .title(payload.getTitle().trim())
                .content(content)
                .scopeType(scope.scopeType())
                .classId(scope.classId())
                .audience(payload.getAudience().trim().toUpperCase(Locale.ROOT))
                .isPinned(Boolean.TRUE.equals(payload.getPinned()) ? 1 : 0)
                .status(DomainConstants.ANNOUNCEMENT_STATUS_DRAFT)
                .expiresAt(payload.getExpiresAt())
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .createdBy(username)
                .build();
        AnnouncementEntity saved = announcementRepository.saveAndFlush(entity);
        log.info("Created announcement id={} by={}", saved.getId(), username);
        return toDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementDto update(Long id, AnnouncementUpsertRequest request) {
        AnnouncementUpsertRequest payload = requirePayload(request);
        AnnouncementEntity entity = requireExisting(id);
        requireTeacherCanManage(entity);
        ScopeResolved scope = resolveScope(payload);
        requireTeacherCanWriteScope(scope);
        entity.setTitle(payload.getTitle().trim());
        entity.setContent(requireSanitizedContent(payload.getContent()));
        entity.setScopeType(scope.scopeType());
        entity.setClassId(scope.classId());
        entity.setAudience(payload.getAudience().trim().toUpperCase(Locale.ROOT));
        if (payload.getPinned() != null) {
            entity.setIsPinned(Boolean.TRUE.equals(payload.getPinned()) ? 1 : 0);
        }
        entity.setExpiresAt(payload.getExpiresAt());
        entity.setUpdatedBy(SecurityUtils.currentUsername());
        return toDto(announcementRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementDto publish(Long id) {
        AnnouncementEntity entity = requireExisting(id);
        requireTeacherCanManage(entity);
        if (DomainConstants.ANNOUNCEMENT_STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw new OracleBusinessException(INVALID_STATUS,
                    "Không thể xuất bản thông báo đã lưu trữ. Hãy tạo bản nháp mới.");
        }
        entity.setStatus(DomainConstants.ANNOUNCEMENT_STATUS_PUBLISHED);
        if (entity.getPublishedAt() == null) {
            entity.setPublishedAt(LocalDateTime.now());
        }
        entity.setUpdatedBy(SecurityUtils.currentUsername());
        return toDto(announcementRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementDto archive(Long id) {
        AnnouncementEntity entity = requireExisting(id);
        requireTeacherCanManage(entity);
        entity.setStatus(DomainConstants.ANNOUNCEMENT_STATUS_ARCHIVED);
        entity.setUpdatedBy(SecurityUtils.currentUsername());
        return toDto(announcementRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        AnnouncementEntity entity = requireExisting(id);
        requireTeacherCanManage(entity);
        entity.setIsDeleted(PersistenceFlags.DELETED);
        entity.setUpdatedBy(SecurityUtils.currentUsername());
        announcementRepository.save(entity);
        log.info("Soft-deleted announcement id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementClassOptionDto> listManageableClasses() {
        Optional<List<ClassEntity>> taught = teachingAssignmentGuard.taughtClassesIfRestricted();
        if (taught.isEmpty()) {
            return List.of();
        }
        return taught.get().stream()
                .map(c -> AnnouncementClassOptionDto.builder()
                        .id(c.getId())
                        .classCode(c.getClassCode())
                        .className(c.getClassName())
                        .build())
                .toList();
    }

    private Specification<AnnouncementEntity> buildSpec(
            AnnouncementFilterRequest f, Optional<Set<Long>> taughtOpt) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            if (f.getStatus() != null && !f.getStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"), f.getStatus().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.getScopeType() != null && !f.getScopeType().isBlank()) {
                predicates.add(cb.equal(root.get("scopeType"), f.getScopeType().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.getClassId() != null) {
                predicates.add(cb.equal(root.get("classId"), f.getClassId()));
            }
            if (f.getAudience() != null && !f.getAudience().isBlank()) {
                predicates.add(cb.equal(root.get("audience"), f.getAudience().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.getKeyword() != null && !f.getKeyword().isBlank()) {
                String like = "%" + f.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), like));
            }
            if (taughtOpt.isPresent()) {
                Set<Long> taught = taughtOpt.get();
                // Giáo viên: ALL (chỉ đọc trên UI) + CLASS thuộc lớp mình dạy.
                Predicate allScope = cb.equal(root.get("scopeType"), DomainConstants.ANNOUNCEMENT_SCOPE_ALL);
                if (taught.isEmpty()) {
                    predicates.add(allScope);
                } else {
                    Predicate ownClass = cb.and(
                            cb.equal(root.get("scopeType"), DomainConstants.ANNOUNCEMENT_SCOPE_CLASS),
                            root.get("classId").in(taught));
                    predicates.add(cb.or(allScope, ownClass));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void requireTeacherCanWriteScope(ScopeResolved scope) {
        if (!teachingAssignmentGuard.isAssignmentRestricted()) {
            return;
        }
        if (DomainConstants.ANNOUNCEMENT_SCOPE_ALL.equals(scope.scopeType())) {
            throw new ForbiddenException(SCOPE_FORBIDDEN, MSG_TEACHER_ALL_FORBIDDEN);
        }
        ClassEntity clazz = classRepository.findByIdAndIsDeleted(scope.classId(), PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(CLASS_NOT_FOUND,
                        "Không tìm thấy lớp với ID: " + scope.classId()));
        teachingAssignmentGuard.requireCanWrite(clazz, MSG_TEACHER_CLASS_FORBIDDEN);
    }

    private void requireTeacherCanManage(AnnouncementEntity entity) {
        if (!teachingAssignmentGuard.isAssignmentRestricted()) {
            return;
        }
        if (!DomainConstants.ANNOUNCEMENT_SCOPE_CLASS.equals(entity.getScopeType())
                || entity.getClassId() == null) {
            throw new ForbiddenException(SCOPE_FORBIDDEN, MSG_TEACHER_MANAGE_FORBIDDEN);
        }
        ClassEntity clazz = classRepository.findByIdAndIsDeleted(entity.getClassId(), PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(CLASS_NOT_FOUND,
                        "Không tìm thấy lớp với ID: " + entity.getClassId()));
        teachingAssignmentGuard.requireCanWrite(clazz, MSG_TEACHER_MANAGE_FORBIDDEN);
    }

    private void requireTeacherCanView(AnnouncementEntity entity) {
        Optional<Set<Long>> taughtOpt = teachingAssignmentGuard.taughtClassIdsIfRestricted();
        if (taughtOpt.isEmpty()) {
            return;
        }
        if (DomainConstants.ANNOUNCEMENT_SCOPE_ALL.equals(entity.getScopeType())) {
            return;
        }
        Set<Long> taught = taughtOpt.get();
        if (DomainConstants.ANNOUNCEMENT_SCOPE_CLASS.equals(entity.getScopeType())
                && entity.getClassId() != null
                && taught.contains(entity.getClassId())) {
            return;
        }
        throw new ForbiddenException(SCOPE_FORBIDDEN, MSG_TEACHER_MANAGE_FORBIDDEN);
    }

    private boolean canCurrentUserManage(AnnouncementEntity entity) {
        if (!teachingAssignmentGuard.isAssignmentRestricted()) {
            return true;
        }
        if (!DomainConstants.ANNOUNCEMENT_SCOPE_CLASS.equals(entity.getScopeType())
                || entity.getClassId() == null) {
            return false;
        }
        Optional<Set<Long>> taughtOpt = teachingAssignmentGuard.taughtClassIdsIfRestricted();
        return taughtOpt.isPresent() && taughtOpt.get().contains(entity.getClassId());
    }

    private AnnouncementEntity requireExisting(Long id) {
        if (id == null) {
            throw new OracleBusinessException("ANNOUNCEMENT_ID_REQUIRED", "ID thông báo không được để trống.");
        }
        return announcementRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(NOT_FOUND, "Không tìm thấy thông báo với ID: " + id));
    }

    private static AnnouncementUpsertRequest requirePayload(AnnouncementUpsertRequest request) {
        if (request == null) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Thiếu dữ liệu thông báo.");
        }
        return request;
    }

    private String requireSanitizedContent(String raw) {
        String sanitized = htmlContentSanitizer.sanitize(raw);
        if (sanitized == null || sanitized.isBlank()) {
            throw new OracleBusinessException(CONTENT_EMPTY, "Nội dung thông báo không được để trống.");
        }
        return sanitized;
    }

    private ScopeResolved resolveScope(AnnouncementUpsertRequest payload) {
        String scope = payload.getScopeType().trim().toUpperCase(Locale.ROOT);
        if (DomainConstants.ANNOUNCEMENT_SCOPE_ALL.equals(scope)) {
            if (payload.getClassId() != null) {
                throw new OracleBusinessException(INVALID_SCOPE,
                        "Phạm vi ALL không được kèm classId.");
            }
            return new ScopeResolved(scope, null);
        }
        if (DomainConstants.ANNOUNCEMENT_SCOPE_CLASS.equals(scope)) {
            if (payload.getClassId() == null) {
                throw new OracleBusinessException(CLASS_REQUIRED,
                        "Phạm vi CLASS yêu cầu classId.");
            }
            classRepository.findByIdAndIsDeleted(payload.getClassId(), PersistenceFlags.NOT_DELETED)
                    .orElseThrow(() -> new OracleBusinessException(CLASS_NOT_FOUND,
                            "Không tìm thấy lớp với ID: " + payload.getClassId()));
            return new ScopeResolved(scope, payload.getClassId());
        }
        throw new OracleBusinessException(INVALID_SCOPE, "Phạm vi không hợp lệ: " + scope);
    }

    private AnnouncementDto toDto(AnnouncementEntity entity) {
        String classCode = null;
        String className = null;
        if (entity.getClassId() != null) {
            ClassEntity clazz = entity.getClazz();
            if (clazz == null) {
                clazz = classRepository.findByIdAndIsDeleted(entity.getClassId(), PersistenceFlags.NOT_DELETED)
                        .orElse(null);
            }
            if (clazz != null) {
                classCode = clazz.getClassCode();
                className = clazz.getClassName();
            }
        }
        return AnnouncementDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .scopeType(entity.getScopeType())
                .classId(entity.getClassId())
                .classCode(classCode)
                .className(className)
                .audience(entity.getAudience())
                .pinned(Integer.valueOf(1).equals(entity.getIsPinned()))
                .status(entity.getStatus())
                .publishedAt(entity.getPublishedAt())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .canManage(canCurrentUserManage(entity))
                .build();
    }

    private record ScopeResolved(String scopeType, Long classId) {
    }
}