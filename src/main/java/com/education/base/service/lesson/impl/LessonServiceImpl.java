package com.education.base.service.lesson.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.file.FileConstants;
import com.education.base.common.lesson.LessonConstants;
import com.education.base.common.lesson.LessonConstants.LessonStatus;
import com.education.base.common.lesson.LessonConstants.OwnerType;
import com.education.base.common.lesson.LessonConstants.ProgressStatus;
import com.education.base.common.lesson.LessonConstants.ResourceType;
import com.education.base.common.lesson.LessonErrorCodes;
import com.education.base.dto.request.LessonReorderRequest;
import com.education.base.dto.request.LessonResourceReorderRequest;
import com.education.base.dto.request.LessonResourceRequest;
import com.education.base.dto.request.LessonSectionUpsertRequest;
import com.education.base.dto.request.LessonUpsertRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.ClassLessonProgressDto;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.LessonDto;
import com.education.base.dto.response.LessonResourceDto;
import com.education.base.dto.response.LessonSectionDto;
import com.education.base.dto.response.LessonSummaryDto;
import com.education.base.dto.response.LessonTreeDto;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.CourseEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonProgressEntity;
import com.education.base.entity.LessonResourceEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.LessonProgressRepository;
import com.education.base.repository.LessonRepository;
import com.education.base.repository.LessonResourceRepository;
import com.education.base.repository.LessonSectionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.impl.TeachingAssignmentGuard;
import com.education.base.service.lesson.LessonService;
import com.education.base.service.lesson.VideoEmbedResolver;
import com.education.base.service.lesson.impl.LessonAccess.Op;
import com.education.base.service.lesson.impl.LessonAccess.Owner;
import com.education.base.service.lesson.impl.LessonCatalog.Group;
import com.education.base.common.DomainConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Chương / bài học / tài nguyên phía nhân viên (§2.2 hợp đồng GĐ3). */
@Service
@RequiredArgsConstructor
public class LessonServiceImpl implements LessonService {

    static final List<String> ACTIVE_CLASS_STATUSES = List.of("PLANNED", "OPEN", "ONGOING");

    private final LessonSectionRepository sectionRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository resourceRepository;
    private final LessonProgressRepository progressRepository;
    private final ClassRepository classRepository;
    private final ClassSessionRepository sessionRepository;
    private final ClassStudentRepository classStudentRepository;
    private final StudentRepository studentRepository;
    private final AssignmentRepository assignmentRepository;
    private final FileStorageService fileStorageService;
    private final FileLifecycleService fileLifecycleService;
    private final FileMapper fileMapper;
    private final TeachingAssignmentGuard teachingAssignmentGuard;
    private final HtmlContentSanitizer htmlContentSanitizer;
    private final VideoEmbedResolver videoEmbedResolver;
    private final AuditService auditService;
    private final LessonAccess access;
    private final LessonCatalog catalog;
    private final Clock clock;

    // ============================================================================================ cây

    @Override
    @Transactional(readOnly = true)
    public LessonTreeDto getTree(String ownerType, Long ownerId) {
        Owner owner = access.owner(ownerType, ownerId);
        access.require(owner, Op.READ);
        return buildTree(owner);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementClassOptionDto> listManageableClasses() {
        List<ClassEntity> classes = teachingAssignmentGuard.taughtClassesIfRestricted()
                .orElseGet(() -> classRepository.findByStatusInAndIsDeletedOrderByCreatedAtDesc(
                        ACTIVE_CLASS_STATUSES, PersistenceFlags.NOT_DELETED));
        return classes.stream()
                .map(c -> AnnouncementClassOptionDto.builder()
                        .id(c.getId()).classCode(c.getClassCode()).className(c.getClassName()).build())
                .toList();
    }

    // ============================================================================================ chương

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonSectionDto createSection(LessonSectionUpsertRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw invalid("thiếu tiêu đề chương");
        }
        Owner owner = access.owner(request.getOwnerType(), request.getOwnerId());
        access.require(owner, owner.isClass() ? Op.CREATE : Op.UPDATE);
        long count = owner.isClass()
                ? sectionRepository.countByClassIdAndIsDeleted(owner.id(), PersistenceFlags.NOT_DELETED)
                : sectionRepository.countByCourseIdAndIsDeleted(owner.id(), PersistenceFlags.NOT_DELETED);
        if (count >= LessonConstants.MAX_SECTIONS_PER_OWNER) {
            throw invalid("tối đa " + LessonConstants.MAX_SECTIONS_PER_OWNER + " chương");
        }
        LessonSectionEntity s = new LessonSectionEntity();
        if (owner.isClass()) {
            s.setClassId(owner.id());
        } else {
            s.setCourseId(owner.id());
        }
        s.setTitle(request.getTitle().strip());
        s.setSortOrder((int) count);
        s.setIsDeleted(PersistenceFlags.NOT_DELETED);
        s.setCreatedAt(now());
        s.setCreatedBy(SecurityUtils.currentUsername());
        LessonSectionEntity saved = sectionRepository.saveAndFlush(s);
        auditSection(saved, "CREATED");
        return toSectionDto(new Group(saved, owner.type(), List.of()), Map.of(), Map.of(), now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonSectionDto updateSection(Long sectionId, LessonSectionUpsertRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw invalid("thiếu tiêu đề chương");
        }
        LessonSectionEntity s = requireSection(sectionId);
        Owner owner = access.ownerOf(s);
        access.require(owner, Op.UPDATE);
        s.setTitle(request.getTitle().strip());
        s.setUpdatedBy(SecurityUtils.currentUsername());
        LessonSectionEntity saved = sectionRepository.saveAndFlush(s);
        auditSection(saved, "UPDATED");
        List<Group> g = catalog.groups(List.of(saved), owner.type());
        List<LessonEntity> lessons = LessonCatalog.flatten(g);
        return toSectionDto(g.get(0), catalog.sessions(lessons), resourceCounts(lessons), now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSection(Long sectionId) {
        LessonSectionEntity s = requireSection(sectionId);
        access.require(access.ownerOf(s), Op.DELETE);
        if (lessonRepository.countBySectionIdAndIsDeleted(s.getId(), PersistenceFlags.NOT_DELETED) > 0) {
            throw new OracleBusinessException(LessonErrorCodes.LESSON_SECTION_NOT_EMPTY,
                    "Chương còn bài học, hãy xoá hoặc chuyển bài trước.");
        }
        s.setIsDeleted(PersistenceFlags.DELETED);
        s.setUpdatedBy(SecurityUtils.currentUsername());
        sectionRepository.saveAndFlush(s);
        auditSection(s, "DELETED");
    }

    // ============================================================================================ bài

    @Override
    @Transactional(readOnly = true)
    public LessonDto getById(Long id) {
        LessonEntity l = requireLesson(id);
        Owner owner = access.ownerOf(l);
        access.require(owner, Op.READ);
        return toDto(l, owner);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonDto create(LessonUpsertRequest request) {
        requirePayload(request);
        LessonSectionEntity section = requireSection(request.getSectionId());
        Owner owner = access.ownerOf(section);
        access.require(owner, owner.isClass() ? Op.CREATE : Op.UPDATE);
        long count = lessonRepository.countBySectionIdAndIsDeleted(section.getId(), PersistenceFlags.NOT_DELETED);
        if (count >= LessonConstants.MAX_LESSONS_PER_SECTION) {
            throw invalid("mỗi chương tối đa " + LessonConstants.MAX_LESSONS_PER_SECTION + " bài");
        }
        validateSession(owner, request.getSessionId());
        LessonEntity l = new LessonEntity();
        l.setSectionId(section.getId());
        l.setClassId(section.getClassId());
        l.setCourseId(section.getCourseId());
        apply(l, request);
        l.setSessionId(request.getSessionId());
        l.setSortOrder((int) count);
        l.setStatus(LessonStatus.DRAFT);
        l.setIsDeleted(PersistenceFlags.NOT_DELETED);
        l.setCreatedAt(now());
        l.setCreatedBy(SecurityUtils.currentUsername());
        LessonEntity saved = lessonRepository.saveAndFlush(l);
        auditLesson(AuditActions.LESSON_CREATED, saved, Map.of());
        return toDto(saved, owner);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonDto update(Long id, LessonUpsertRequest request) {
        requirePayload(request);
        LessonEntity l = requireLesson(id);
        Owner owner = access.ownerOf(l);
        access.require(owner, Op.UPDATE);
        if (!Objects.equals(l.getSectionId(), request.getSectionId())) {
            LessonSectionEntity target = requireSection(request.getSectionId());
            if (!Objects.equals(target.getClassId(), l.getClassId()) || !Objects.equals(target.getCourseId(), l.getCourseId())) {
                throw invalid("không chuyển bài sang chương của chủ khác");
            }
            long count = lessonRepository.countBySectionIdAndIsDeleted(target.getId(), PersistenceFlags.NOT_DELETED);
            if (count >= LessonConstants.MAX_LESSONS_PER_SECTION) {
                throw invalid("mỗi chương tối đa " + LessonConstants.MAX_LESSONS_PER_SECTION + " bài");
            }
            l.setSectionId(target.getId());
            l.setSortOrder((int) count);
        }
        if (!Objects.equals(l.getSessionId(), request.getSessionId())) {
            validateSession(owner, request.getSessionId());
            l.setSessionId(request.getSessionId());
        }
        apply(l, request);
        l.setUpdatedBy(SecurityUtils.currentUsername());
        LessonEntity saved = lessonRepository.saveAndFlush(l);
        auditLesson(AuditActions.LESSON_UPDATED, saved, Map.of());
        return toDto(saved, owner);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        LessonEntity l = requireLesson(id);
        access.require(access.ownerOf(l), Op.DELETE);
        for (LessonResourceEntity r : resourceRepository.findByLessonIdAndIsDeletedOrderBySortOrderAscIdAsc(l.getId(),
                PersistenceFlags.NOT_DELETED)) {
            removeResource(r, "Xoá bài học");
        }
        l.setIsDeleted(PersistenceFlags.DELETED);
        l.setUpdatedBy(SecurityUtils.currentUsername());
        lessonRepository.saveAndFlush(l);
        auditLesson(AuditActions.LESSON_DELETED, l, Map.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonDto publish(Long id) {
        LessonEntity l = requireLesson(id);
        Owner owner = access.ownerOf(l);
        access.require(owner, Op.UPDATE);
        if (LessonStatus.PUBLISHED.equals(l.getStatus())) {
            throw invalidState();
        }
        l.setStatus(LessonStatus.PUBLISHED);
        if (l.getPublishedAt() == null) {
            l.setPublishedAt(now());
        }
        l.setUpdatedBy(SecurityUtils.currentUsername());
        LessonEntity saved = lessonRepository.saveAndFlush(l);
        auditLesson(AuditActions.LESSON_PUBLISHED, saved, Map.of());
        return toDto(saved, owner);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonDto unpublish(Long id) {
        LessonEntity l = requireLesson(id);
        Owner owner = access.ownerOf(l);
        access.require(owner, Op.UPDATE);
        if (!LessonStatus.PUBLISHED.equals(l.getStatus())) {
            throw invalidState();
        }
        l.setStatus(LessonStatus.DRAFT);
        l.setUpdatedBy(SecurityUtils.currentUsername());
        LessonEntity saved = lessonRepository.saveAndFlush(l);
        auditLesson(AuditActions.LESSON_UNPUBLISHED, saved, Map.of());
        return toDto(saved, owner);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonTreeDto reorder(LessonReorderRequest request) {
        if (request == null || request.getSections() == null) {
            throw reorderInvalid();
        }
        Owner owner = access.owner(request.getOwnerType(), request.getOwnerId());
        access.require(owner, Op.UPDATE);
        List<Group> groups = catalog.ownerGroups(owner.type(), owner.id());
        Map<Long, LessonSectionEntity> sections = new HashMap<>();
        Map<Long, LessonEntity> lessons = new HashMap<>();
        for (Group g : groups) {
            sections.put(g.section().getId(), g.section());
            g.lessons().forEach(l -> lessons.put(l.getId(), l));
        }
        Set<Long> seenSections = new HashSet<>();
        Set<Long> seenLessons = new HashSet<>();
        for (LessonReorderRequest.SectionOrder so : request.getSections()) {
            if (so == null || so.getSectionId() == null || so.getLessonIds() == null
                    || !sections.containsKey(so.getSectionId()) || !seenSections.add(so.getSectionId())) {
                throw reorderInvalid();
            }
            for (Long lid : so.getLessonIds()) {
                if (lid == null || !lessons.containsKey(lid) || !seenLessons.add(lid)) {
                    throw reorderInvalid();
                }
            }
            if (so.getLessonIds().size() > LessonConstants.MAX_LESSONS_PER_SECTION) {
                throw reorderInvalid();
            }
        }
        if (seenSections.size() != sections.size() || seenLessons.size() != lessons.size()) {
            throw reorderInvalid();
        }
        String by = SecurityUtils.currentUsername();
        int si = 0;
        for (LessonReorderRequest.SectionOrder so : request.getSections()) {
            LessonSectionEntity s = sections.get(so.getSectionId());
            if (!Objects.equals(s.getSortOrder(), si)) {
                s.setSortOrder(si);
                s.setUpdatedBy(by);
                sectionRepository.save(s);
            }
            si++;
            int li = 0;
            for (Long lid : so.getLessonIds()) {
                LessonEntity l = lessons.get(lid);
                if (!Objects.equals(l.getSortOrder(), li) || !Objects.equals(l.getSectionId(), s.getId())) {
                    l.setSortOrder(li);
                    l.setSectionId(s.getId());
                    l.setUpdatedBy(by);
                    lessonRepository.save(l);
                }
                li++;
            }
        }
        sectionRepository.flush();
        lessonRepository.flush();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("ownerType", owner.type());
        d.put("ownerId", owner.id());
        auditService.success(AuditActions.LESSON_REORDERED, owner.isClass() ? "CLASS" : AuditActions.RESOURCE_COURSE,
                owner.id(), d);
        return buildTree(owner);
    }

    // ============================================================================================ tài nguyên

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonResourceDto addResource(Long lessonId, LessonResourceRequest request) {
        LessonEntity l = requireLesson(lessonId);
        access.require(access.ownerOf(l), Op.UPDATE);
        String type = request == null || request.getResourceType() == null ? ""
                : request.getResourceType().trim().toUpperCase(Locale.ROOT);
        if (!(ResourceType.VIDEO.equals(type) || ResourceType.LINK.equals(type) || ResourceType.ASSIGNMENT.equals(type))) {
            throw resourceInvalid("Loại tài liệu không hợp lệ (FILE tải lên bằng multipart).");
        }
        long count = requireResourceCapacity(l);
        LessonResourceEntity r = new LessonResourceEntity();
        r.setLessonId(l.getId());
        r.setResourceType(type);
        applyResource(r, l, request);
        r.setSortOrder((int) count);
        r.setIsDeleted(PersistenceFlags.NOT_DELETED);
        r.setCreatedAt(now());
        r.setCreatedBy(SecurityUtils.currentUsername());
        LessonResourceEntity saved = resourceRepository.saveAndFlush(r);
        auditResource(AuditActions.LESSON_RESOURCE_ADDED, saved);
        return toResourceDto(saved, assignments(List.of(saved)), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonResourceDto uploadResourceFile(Long lessonId, MultipartFile file, String title) {
        LessonEntity l = requireLesson(lessonId);
        access.require(access.ownerOf(l), Op.UPDATE);
        if (file == null || file.isEmpty()) {
            throw resourceInvalid("Chưa chọn file.");
        }
        if (title != null && title.strip().length() > LessonConstants.TITLE_MAX) {
            throw resourceInvalid("Tiêu đề quá dài.");
        }
        long count = requireResourceCapacity(l);
        FileEntity stored = fileStorageService.storeFile(file, FileConstants.Module.LESSON, l.getId());
        LessonResourceEntity r = new LessonResourceEntity();
        r.setLessonId(l.getId());
        r.setResourceType(ResourceType.FILE);
        r.setFileId(stored.getId());
        r.setTitle(title != null && !title.isBlank() ? title.strip() : truncate(stored.getOriginalName()));
        r.setSortOrder((int) count);
        r.setIsDeleted(PersistenceFlags.NOT_DELETED);
        r.setCreatedAt(now());
        r.setCreatedBy(SecurityUtils.currentUsername());
        LessonResourceEntity saved = resourceRepository.saveAndFlush(r);
        auditResource(AuditActions.LESSON_RESOURCE_ADDED, saved);
        return toResourceDto(saved, Map.of(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonResourceDto updateResource(Long resourceId, LessonResourceRequest request) {
        LessonResourceEntity r = requireResource(resourceId);
        LessonEntity l = requireLesson(r.getLessonId());
        access.require(access.ownerOf(l), Op.UPDATE);
        String type = request == null || request.getResourceType() == null ? ""
                : request.getResourceType().trim().toUpperCase(Locale.ROOT);
        if (!Objects.equals(type, r.getResourceType())) {
            throw resourceInvalid("Không đổi được loại tài liệu.");
        }
        if (ResourceType.FILE.equals(type)) {
            if (request.getTitle() != null && !request.getTitle().isBlank()) {
                r.setTitle(request.getTitle().strip());
            }
        } else {
            applyResource(r, l, request);
        }
        r.setUpdatedBy(SecurityUtils.currentUsername());
        LessonResourceEntity saved = resourceRepository.saveAndFlush(r);
        return toResourceDto(saved, assignments(List.of(saved)), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResource(Long resourceId) {
        LessonResourceEntity r = requireResource(resourceId);
        LessonEntity l = requireLesson(r.getLessonId());
        access.require(access.ownerOf(l), Op.UPDATE);
        removeResource(r, "Gỡ tài liệu bài học");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LessonResourceDto> reorderResources(Long lessonId, LessonResourceReorderRequest request) {
        LessonEntity l = requireLesson(lessonId);
        access.require(access.ownerOf(l), Op.UPDATE);
        List<LessonResourceEntity> current = resourceRepository.findByLessonIdAndIsDeletedOrderBySortOrderAscIdAsc(
                l.getId(), PersistenceFlags.NOT_DELETED);
        Map<Long, LessonResourceEntity> byId = LessonCatalog.index(current, LessonResourceEntity::getId);
        List<Long> ids = request == null ? null : request.getResourceIds();
        if (ids == null || ids.size() != current.size() || new HashSet<>(ids).size() != ids.size()
                || !byId.keySet().containsAll(ids)) {
            throw reorderInvalid();
        }
        List<LessonResourceEntity> ordered = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            LessonResourceEntity r = byId.get(ids.get(i));
            if (!Objects.equals(r.getSortOrder(), i)) {
                r.setSortOrder(i);
                r.setUpdatedBy(SecurityUtils.currentUsername());
                resourceRepository.save(r);
            }
            ordered.add(r);
        }
        resourceRepository.flush();
        Map<Long, AssignmentEntity> a = assignments(ordered);
        return ordered.stream().map(r -> toResourceDto(r, a, true)).toList();
    }

    // ============================================================================================ tiến độ

    @Override
    @Transactional(readOnly = true)
    public ClassLessonProgressDto getClassProgress(Long classId) {
        ClassEntity clazz = access.requireClass(classId);
        access.requireClassScope(clazz);
        LocalDateTime now = now();
        List<Group> groups = catalog.classGroups(clazz);
        List<LessonEntity> all = LessonCatalog.flatten(groups);
        Map<Long, ClassSessionEntity> sessions = catalog.sessions(all);
        Map<Long, Group> groupOfLesson = new HashMap<>();
        groups.forEach(g -> g.lessons().forEach(l -> groupOfLesson.put(l.getId(), g)));
        List<LessonEntity> available = all.stream()
                .filter(l -> LessonRules.availableNow(l, sessions.get(l.getSessionId()), now)).toList();
        List<Long> lessonIds = available.stream().map(LessonEntity::getId).toList();

        List<ClassStudentEntity> enrollments = classStudentRepository.findByClassIdAndStatusAndIsDeleted(
                clazz.getId(), DomainConstants.ENROLLMENT_STATUS_ENROLLED, PersistenceFlags.NOT_DELETED);
        List<Long> studentIds = enrollments.stream().map(ClassStudentEntity::getStudentId).distinct().toList();
        Map<Long, StudentEntity> students = new HashMap<>();
        for (List<Long> part : LessonCatalog.chunks(studentIds)) {
            studentRepository.findAllById(part).forEach(s -> students.put(s.getId(), s));
        }
        Set<Long> studentSet = new HashSet<>(studentIds);
        Map<Long, Map<Long, LessonProgressEntity>> progress = new HashMap<>();
        for (List<Long> part : LessonCatalog.chunks(lessonIds)) {
            for (LessonProgressEntity p : progressRepository.findByLessonIdIn(part)) {
                if (studentSet.contains(p.getStudentId())) {
                    progress.computeIfAbsent(p.getStudentId(), k -> new HashMap<>()).put(p.getLessonId(), p);
                }
            }
        }
        Map<Long, Integer> completedPerLesson = new HashMap<>();
        List<ClassLessonProgressDto.StudentRow> rows = new ArrayList<>();
        for (Long sid : studentIds) {
            Map<Long, LessonProgressEntity> mine = progress.getOrDefault(sid, Map.of());
            Map<Long, String> statuses = new LinkedHashMap<>();
            int done = 0;
            LocalDateTime last = null;
            for (Long lid : lessonIds) {
                LessonProgressEntity p = mine.get(lid);
                String st = p == null ? ProgressStatus.NOT_STARTED : p.getStatus();
                statuses.put(lid, st);
                if (ProgressStatus.COMPLETED.equals(st)) {
                    done++;
                    completedPerLesson.merge(lid, 1, Integer::sum);
                }
                if (p != null && p.getLastViewedAt() != null && (last == null || p.getLastViewedAt().isAfter(last))) {
                    last = p.getLastViewedAt();
                }
            }
            StudentEntity st = students.get(sid);
            rows.add(ClassLessonProgressDto.StudentRow.builder()
                    .studentId(sid)
                    .studentCode(st == null ? null : st.getStudentCode())
                    .fullName(st == null ? null : st.getFullName())
                    .completedCount(done)
                    .totalLessons(lessonIds.size())
                    .percent(LessonRules.percent(done, lessonIds.size()))
                    .lastViewedAt(last)
                    .statuses(statuses)
                    .build());
        }
        rows.sort((a, b) -> String.valueOf(a.getFullName()).compareToIgnoreCase(String.valueOf(b.getFullName())));
        List<ClassLessonProgressDto.LessonColumn> columns = available.stream()
                .map(l -> ClassLessonProgressDto.LessonColumn.builder()
                        .lessonId(l.getId())
                        .title(l.getTitle())
                        .sectionTitle(groupOfLesson.get(l.getId()).section().getTitle())
                        .source(groupOfLesson.get(l.getId()).source())
                        .availableNow(true)
                        .completedCount(completedPerLesson.getOrDefault(l.getId(), 0))
                        .build())
                .toList();
        return ClassLessonProgressDto.builder()
                .classId(clazz.getId())
                .classCode(clazz.getClassCode())
                .className(clazz.getClassName())
                .lessons(columns)
                .rows(rows)
                .build();
    }

    // ============================================================================================ nội bộ

    LessonTreeDto buildTree(Owner owner) {
        LocalDateTime now = now();
        List<Group> own = catalog.ownerGroups(owner.type(), owner.id());
        List<Group> inherited = owner.isClass() ? catalog.inheritedGroups(owner.clazz()) : List.of();
        List<LessonEntity> all = new ArrayList<>(LessonCatalog.flatten(own));
        all.addAll(LessonCatalog.flatten(inherited));
        Map<Long, ClassSessionEntity> sessions = catalog.sessions(all);
        Map<Long, Integer> counts = resourceCounts(all);
        CourseEntity linkedCourse = null;
        if (owner.isClass() && owner.clazz().getCourseId() != null && !inherited.isEmpty()) {
            linkedCourse = access.requireCourse(owner.clazz().getCourseId());
        } else if (owner.isClass() && owner.clazz().getCourseId() != null) {
            try {
                linkedCourse = access.requireCourse(owner.clazz().getCourseId());
            } catch (OracleBusinessException ignored) {
                linkedCourse = null;
            }
        }
        return LessonTreeDto.builder()
                .ownerType(owner.type())
                .ownerId(owner.id())
                .ownerCode(owner.isClass() ? owner.clazz().getClassCode() : owner.course().getCourseCode())
                .ownerName(owner.isClass() ? owner.clazz().getClassName() : owner.course().getCourseName())
                .courseId(owner.isClass() ? (linkedCourse == null ? null : linkedCourse.getId()) : owner.id())
                .courseName(owner.isClass() ? (linkedCourse == null ? null : linkedCourse.getCourseName())
                        : owner.course().getCourseName())
                .sections(own.stream().map(g -> toSectionDto(g, sessions, counts, now)).toList())
                .inheritedSections(owner.isClass()
                        ? inherited.stream().map(g -> toSectionDto(g, sessions, counts, now)).toList() : null)
                .canManage(access.can(owner, Op.UPDATE))
                .build();
    }

    private LessonSectionDto toSectionDto(Group g, Map<Long, ClassSessionEntity> sessions, Map<Long, Integer> counts,
                                          LocalDateTime now) {
        LessonSectionEntity s = g.section();
        return LessonSectionDto.builder()
                .id(s.getId())
                .ownerType(s.getClassId() != null ? OwnerType.CLASS : OwnerType.COURSE)
                .ownerId(s.getClassId() != null ? s.getClassId() : s.getCourseId())
                .title(s.getTitle())
                .sortOrder(s.getSortOrder())
                .version(s.getVersion())
                .lessons(g.lessons().stream().map(l -> toSummary(l, sessions.get(l.getSessionId()),
                        counts.getOrDefault(l.getId(), 0), now)).toList())
                .build();
    }

    private static LessonSummaryDto toSummary(LessonEntity l, ClassSessionEntity session, int resourceCount,
                                              LocalDateTime now) {
        return LessonSummaryDto.builder()
                .id(l.getId())
                .sectionId(l.getSectionId())
                .title(l.getTitle())
                .status(l.getStatus())
                .sortOrder(l.getSortOrder())
                .availableFrom(l.getAvailableFrom())
                .effectiveAvailableFrom(LessonRules.effectiveAvailableFrom(l, session))
                .sessionId(l.getSessionId())
                .sessionDate(session == null ? null : session.getSessionDate())
                .estimatedMinutes(l.getEstimatedMinutes())
                .resourceCount(resourceCount)
                .availableNow(LessonRules.availableNow(l, session, now))
                .build();
    }

    LessonDto toDto(LessonEntity l, Owner owner) {
        LocalDateTime now = now();
        ClassSessionEntity session = l.getSessionId() == null ? null
                : sessionRepository.findById(l.getSessionId()).orElse(null);
        LessonSectionEntity section = sectionRepository.findById(l.getSectionId()).orElse(null);
        boolean canManage = access.can(owner, Op.UPDATE);
        List<LessonResourceEntity> resources = resourceRepository.findByLessonIdAndIsDeletedOrderBySortOrderAscIdAsc(
                l.getId(), PersistenceFlags.NOT_DELETED);
        Map<Long, AssignmentEntity> assignments = assignments(resources);
        return LessonDto.builder()
                .id(l.getId())
                .sectionId(l.getSectionId())
                .sectionTitle(section == null ? null : section.getTitle())
                .ownerType(owner.type())
                .ownerId(owner.id())
                .courseId(l.getCourseId())
                .classId(l.getClassId())
                .sessionId(l.getSessionId())
                .sessionDate(session == null ? null : session.getSessionDate())
                .title(l.getTitle())
                .content(l.getContent())
                .sortOrder(l.getSortOrder())
                .status(l.getStatus())
                .availableFrom(l.getAvailableFrom())
                .effectiveAvailableFrom(LessonRules.effectiveAvailableFrom(l, session))
                .availableNow(LessonRules.availableNow(l, session, now))
                .estimatedMinutes(l.getEstimatedMinutes())
                .publishedAt(l.getPublishedAt())
                .sourceLessonId(l.getSourceLessonId())
                .resources(resources.stream().map(r -> toResourceDto(r, assignments, canManage)).toList())
                .version(l.getVersion())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .createdBy(l.getCreatedBy())
                .updatedBy(l.getUpdatedBy())
                .canManage(canManage)
                .serverNow(now)
                .build();
    }

    private LessonResourceDto toResourceDto(LessonResourceEntity r, Map<Long, AssignmentEntity> assignments,
                                            boolean canManage) {
        FileResponseDto file = null;
        if (r.getFileId() != null) {
            try {
                file = fileMapper.toDto(fileStorageService.getFileEntity(r.getFileId()));
                if (file != null) {
                    file.setCanDelete(canManage);
                }
            } catch (OracleBusinessException e) {
                file = null;
            }
        }
        AssignmentEntity a = r.getAssignmentId() == null ? null : assignments.get(r.getAssignmentId());
        return LessonResourceDto.builder()
                .id(r.getId())
                .lessonId(r.getLessonId())
                .resourceType(r.getResourceType())
                .title(r.getTitle())
                .sortOrder(r.getSortOrder())
                .file(file)
                .url(r.getUrl())
                .embedUrl(r.getEmbedUrl())
                .provider(r.getProvider())
                .assignmentId(r.getAssignmentId())
                .assignmentTitle(a == null ? null : a.getTitle())
                .assignmentStatus(a == null ? null : a.getStatus())
                .assignmentDueAt(a == null ? null : a.getDueAt())
                .build();
    }

    private Map<Long, AssignmentEntity> assignments(List<LessonResourceEntity> resources) {
        List<Long> ids = resources.stream().map(LessonResourceEntity::getAssignmentId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, AssignmentEntity> out = new HashMap<>();
        if (!ids.isEmpty()) {
            assignmentRepository.findAllById(ids).forEach(a -> out.put(a.getId(), a));
        }
        return out;
    }

    private Map<Long, Integer> resourceCounts(List<LessonEntity> lessons) {
        Map<Long, Integer> out = new HashMap<>();
        catalog.resources(lessons.stream().map(LessonEntity::getId).toList())
                .forEach((k, v) -> out.put(k, v.size()));
        return out;
    }

    private void apply(LessonEntity l, LessonUpsertRequest r) {
        l.setTitle(r.getTitle().strip());
        String content = sanitizeOrNull(r.getContent());
        if (content != null && content.length() > LessonConstants.CONTENT_MAX) {
            throw invalid("nội dung quá dài");
        }
        l.setContent(content);
        l.setAvailableFrom(r.getAvailableFrom());
        l.setEstimatedMinutes(r.getEstimatedMinutes());
    }

    private void applyResource(LessonResourceEntity r, LessonEntity l, LessonResourceRequest req) {
        String title = req.getTitle() == null || req.getTitle().isBlank() ? null : req.getTitle().strip();
        switch (r.getResourceType()) {
            case ResourceType.VIDEO -> {
                VideoEmbedResolver.VideoEmbed v = videoEmbedResolver.resolve(req.getUrl());
                r.setUrl(v.normalizedUrl());
                r.setEmbedUrl(v.embedUrl());
                r.setProvider(v.provider());
                r.setTitle(title != null ? title : "Video");
            }
            case ResourceType.LINK -> {
                URI uri = requireHttpUrl(req.getUrl());
                r.setUrl(req.getUrl().trim());
                r.setEmbedUrl(null);
                r.setProvider(VideoEmbedResolverImpl.providerOfHost(uri.getHost()));
                r.setTitle(title != null ? title : truncate(uri.getHost()));
            }
            case ResourceType.ASSIGNMENT -> {
                AssignmentEntity a = requireAssignmentOfClass(l, req.getAssignmentId());
                r.setAssignmentId(a.getId());
                r.setTitle(title != null ? title : truncate(a.getTitle()));
            }
            default -> throw resourceInvalid("Loại tài liệu không hợp lệ.");
        }
    }

    /** Chỉ http/https tuyệt đối có host, &lt;= URL_MAX (chặn javascript:, data:, file:). */
    static URI requireHttpUrl(String url) {
        if (url == null || url.isBlank() || url.trim().length() > LessonConstants.URL_MAX) {
            throw urlInvalid();
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw urlInvalid();
            }
            return uri;
        } catch (URISyntaxException e) {
            throw urlInvalid();
        }
    }

    private AssignmentEntity requireAssignmentOfClass(LessonEntity l, Long assignmentId) {
        if (l.getClassId() == null || assignmentId == null) {
            throw assignmentInvalid();
        }
        return assignmentRepository.findByIdAndIsDeleted(assignmentId, PersistenceFlags.NOT_DELETED)
                .filter(a -> Objects.equals(a.getClassId(), l.getClassId()))
                .orElseThrow(LessonServiceImpl::assignmentInvalid);
    }

    private long requireResourceCapacity(LessonEntity l) {
        long count = resourceRepository.countByLessonIdAndIsDeleted(l.getId(), PersistenceFlags.NOT_DELETED);
        if (count >= LessonConstants.MAX_RESOURCES_PER_LESSON) {
            throw resourceInvalid("Mỗi bài tối đa " + LessonConstants.MAX_RESOURCES_PER_LESSON + " tài liệu.");
        }
        return count;
    }

    /** Xoá mềm tài nguyên; FILE chỉ xoá mềm file khi không còn tài nguyên chưa xoá khác dùng FILE_ID. */
    void removeResource(LessonResourceEntity r, String reason) {
        r.setIsDeleted(PersistenceFlags.DELETED);
        r.setUpdatedBy(SecurityUtils.currentUsername());
        resourceRepository.saveAndFlush(r);
        if (ResourceType.FILE.equals(r.getResourceType()) && r.getFileId() != null
                && !resourceRepository.existsOtherLiveByFileId(r.getFileId(), r.getId())) {
            try {
                fileLifecycleService.softDelete(r.getFileId(), reason);
            } catch (OracleBusinessException e) {
                // file đã xoá/không còn: bỏ qua
            }
        }
        auditResource(AuditActions.LESSON_RESOURCE_REMOVED, r);
    }

    private void validateSession(Owner owner, Long sessionId) {
        if (sessionId == null) {
            return;
        }
        if (!owner.isClass()) {
            throw sessionInvalid();
        }
        ClassSessionEntity s = sessionRepository.findByIdAndIsDeleted(sessionId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(LessonServiceImpl::sessionInvalid);
        if (!Objects.equals(s.getClassId(), owner.id()) || LessonRules.SESSION_CANCELLED.equals(s.getStatus())) {
            throw sessionInvalid();
        }
    }

    private static void requirePayload(LessonUpsertRequest r) {
        if (r == null || r.getSectionId() == null || r.getTitle() == null || r.getTitle().isBlank()) {
            throw invalid("thiếu chương hoặc tiêu đề");
        }
    }

    LessonSectionEntity requireSection(Long id) {
        if (id == null) {
            throw sectionNotFound();
        }
        return sectionRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(LessonServiceImpl::sectionNotFound);
    }

    LessonEntity requireLesson(Long id) {
        if (id == null) {
            throw lessonNotFound();
        }
        return lessonRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(LessonServiceImpl::lessonNotFound);
    }

    private LessonResourceEntity requireResource(Long id) {
        if (id == null) {
            throw resourceNotFound();
        }
        return resourceRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(LessonServiceImpl::resourceNotFound);
    }

    private String sanitizeOrNull(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = htmlContentSanitizer.sanitize(html);
        return clean == null || clean.isBlank() ? null : clean;
    }

    private LocalDateTime now() {
        return LessonRules.now(clock);
    }

    private static String truncate(String s) {
        if (s == null || s.isBlank()) {
            return "Tài liệu";
        }
        return s.length() <= LessonConstants.TITLE_MAX ? s : s.substring(0, LessonConstants.TITLE_MAX);
    }

    private void auditSection(LessonSectionEntity s, String op) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("op", op);
        d.put("ownerType", s.getClassId() != null ? OwnerType.CLASS : OwnerType.COURSE);
        d.put("ownerId", s.getClassId() != null ? s.getClassId() : s.getCourseId());
        auditService.success(AuditActions.LESSON_SECTION_CHANGED, AuditActions.RESOURCE_LESSON_SECTION, s.getId(), d);
    }

    private void auditLesson(String action, LessonEntity l, Map<String, ?> extra) {
        Map<String, Object> d = new LinkedHashMap<>(extra);
        d.put("status", l.getStatus());
        d.put("sectionId", l.getSectionId());
        if (l.getClassId() != null) {
            d.put("classId", l.getClassId());
        } else {
            d.put("courseId", l.getCourseId());
        }
        auditService.success(action, AuditActions.RESOURCE_LESSON, l.getId(), d);
    }

    private void auditResource(String action, LessonResourceEntity r) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("lessonId", r.getLessonId());
        d.put("type", r.getResourceType());
        if (r.getFileId() != null) {
            d.put("fileId", r.getFileId());
        }
        auditService.success(action, AuditActions.RESOURCE_LESSON_RESOURCE, r.getId(), d);
    }

    static OracleBusinessException invalid(String msg) {
        return new OracleBusinessException(LessonErrorCodes.LESSON_INVALID, "Thông tin bài học không hợp lệ: " + msg + ".");
    }

    static OracleBusinessException invalidState() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_INVALID_STATE,
                "Không thể thực hiện với trạng thái hiện tại của bài học.");
    }

    static OracleBusinessException reorderInvalid() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_REORDER_INVALID,
                "Danh sách sắp xếp không khớp, hãy tải lại.");
    }

    static OracleBusinessException sessionInvalid() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_SESSION_INVALID, "Buổi học không thuộc lớp của bài.");
    }

    static OracleBusinessException lessonNotFound() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_NOT_FOUND, "Không tìm thấy bài học.");
    }

    static OracleBusinessException sectionNotFound() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_SECTION_NOT_FOUND, "Không tìm thấy chương.");
    }

    static OracleBusinessException resourceNotFound() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_RESOURCE_NOT_FOUND, "Không tìm thấy tài liệu.");
    }

    static OracleBusinessException resourceInvalid(String msg) {
        return new OracleBusinessException(LessonErrorCodes.LESSON_RESOURCE_INVALID, "Tài liệu không hợp lệ. " + msg);
    }

    static OracleBusinessException urlInvalid() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_URL_INVALID, "Đường dẫn không hợp lệ.");
    }

    static OracleBusinessException assignmentInvalid() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_ASSIGNMENT_INVALID,
                "Bài tập không thuộc lớp của bài học.");
    }
}
