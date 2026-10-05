package com.education.base.service.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.dto.response.PortalAnnouncementDto;
import com.education.base.entity.AnnouncementEntity;
import com.education.base.entity.AnnouncementReadEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.UserStudentLinkEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AnnouncementReadRepository;
import com.education.base.repository.AnnouncementRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.UserStudentLinkRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.PortalStudentContext;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AnnouncementQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementQueryServiceImpl implements AnnouncementQueryService {

    public static final String NOT_VISIBLE = "ANNOUNCEMENT_NOT_VISIBLE";

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserStudentLinkRepository userStudentLinkRepository;
    private final PortalStudentContext portalStudentContext;

    @Override
    @Transactional(readOnly = true)
    public List<PortalAnnouncementDto> listForCurrentStudent() {
        AuthUserPrincipal principal = SecurityUtils.requireCurrentUser();
        Long studentId = portalStudentContext.requireCurrentStudentId();
        Set<Long> classIds = enrolledClassIds(studentId);
        List<AnnouncementEntity> list = announcementRepository.findVisibleForStudent(classIds, LocalDateTime.now());
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = list.stream().map(AnnouncementEntity::getId).collect(Collectors.toSet());
        Set<Long> readIds = announcementReadRepository.findReadAnnouncementIds(principal.getId(), ids);
        return list.stream().map(a -> toPortalDto(a, readIds.contains(a.getId()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long announcementId) {
        AuthUserPrincipal principal = SecurityUtils.requireCurrentUser();
        Long studentId = portalStudentContext.requireCurrentStudentId();
        AnnouncementEntity announcement = requireVisible(announcementId, studentId);
        if (announcementReadRepository.existsByAnnouncementIdAndUserId(announcement.getId(), principal.getId())) {
            return;
        }
        announcementReadRepository.save(AnnouncementReadEntity.builder()
                .announcementId(announcement.getId())
                .userId(principal.getId())
                .readAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long studentUserId) {
        if (studentUserId == null) {
            return 0L;
        }
        Long studentId = userStudentLinkRepository.findActiveSelfLinkByUserId(studentUserId)
                .map(UserStudentLinkEntity::getStudentId)
                .orElse(null);
        if (studentId == null) {
            return 0L;
        }
        Set<Long> classIds = enrolledClassIds(studentId);
        return announcementRepository.countUnreadForStudent(studentUserId, classIds, LocalDateTime.now());
    }

    private AnnouncementEntity requireVisible(Long announcementId, Long studentId) {
        if (announcementId == null) {
            throw new OracleBusinessException("ANNOUNCEMENT_ID_REQUIRED", "ID thông báo không được để trống.");
        }
        Set<Long> classIds = enrolledClassIds(studentId);
        return announcementRepository.findVisibleForStudent(classIds, LocalDateTime.now()).stream()
                .filter(a -> a.getId().equals(announcementId))
                .findFirst()
                .orElseThrow(() -> new OracleBusinessException(NOT_VISIBLE,
                        "Không tìm thấy thông báo hoặc bạn không có quyền xem."));
    }

    private Set<Long> enrolledClassIds(Long studentId) {
        List<ClassStudentEntity> enrollments = classStudentRepository.findActiveEnrollmentsWithClass(studentId);
        if (enrollments.isEmpty()) {
            // JPQL "IN :classIds" với collection rỗng có thể lỗi trên một số provider — dùng tập giả không khớp.
            return Set.of(-1L);
        }
        Set<Long> ids = new HashSet<>();
        for (ClassStudentEntity enrollment : enrollments) {
            if (enrollment.getClassId() != null) {
                ids.add(enrollment.getClassId());
            }
        }
        return ids.isEmpty() ? Set.of(-1L) : Collections.unmodifiableSet(ids);
    }

    private PortalAnnouncementDto toPortalDto(AnnouncementEntity entity, boolean read) {
        String className = null;
        if (entity.getClazz() != null) {
            className = entity.getClazz().getClassName();
        }
        return PortalAnnouncementDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .scopeType(entity.getScopeType())
                .classId(entity.getClassId())
                .className(className)
                .pinned(Integer.valueOf(1).equals(entity.getIsPinned()))
                .publishedAt(entity.getPublishedAt())
                .expiresAt(entity.getExpiresAt())
                .read(read)
                .build();
    }
}
