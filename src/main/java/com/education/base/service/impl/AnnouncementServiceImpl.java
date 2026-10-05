package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.AnnouncementFilterRequest;
import com.education.base.dto.request.AnnouncementUpsertRequest;
import com.education.base.dto.response.AnnouncementDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.AnnouncementEntity;
import com.education.base.entity.ClassEntity;
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

    private final AnnouncementRepository announcementRepository;
    private final ClassRepository classRepository;
    private final HtmlContentSanitizer htmlContentSanitizer;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementDto> search(AnnouncementFilterRequest filter) {
        AnnouncementFilterRequest f = filter == null ? new AnnouncementFilterRequest() : filter;
        Specification<AnnouncementEntity> spec = buildSpec(f);
        PageRequest pageable = PageRequest.of(
                Math.max(f.getPageNo(), 0),
                Math.max(f.getPageSize(), 1),
                Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        Page<AnnouncementEntity> page = announcementRepository.findAll(spec, pageable);
        List<AnnouncementDto> content = page.getContent().stream().map(this::toDto).toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementDto getById(Long id) {
        return toDto(requireExisting(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementDto create(AnnouncementUpsertRequest request) {
        AnnouncementUpsertRequest payload = requirePayload(request);
        ScopeResolved scope = resolveScope(payload);
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
        ScopeResolved scope = resolveScope(payload);
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
        entity.setStatus(DomainConstants.ANNOUNCEMENT_STATUS_ARCHIVED);
        entity.setUpdatedBy(SecurityUtils.currentUsername());
        return toDto(announcementRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        AnnouncementEntity entity = requireExisting(id);
        entity.setIsDeleted(PersistenceFlags.DELETED);
        entity.setUpdatedBy(SecurityUtils.currentUsername());
        announcementRepository.save(entity);
        log.info("Soft-deleted announcement id={}", id);
    }

    private Specification<AnnouncementEntity> buildSpec(AnnouncementFilterRequest f) {
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
            return cb.and(predicates.toArray(Predicate[]::new));
        };
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
                .build();
    }

    private record ScopeResolved(String scopeType, Long classId) {
    }
}
