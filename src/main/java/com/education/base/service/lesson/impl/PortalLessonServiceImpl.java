package com.education.base.service.lesson.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.homework.HomeworkConstants.AssignmentStatus;
import com.education.base.common.lesson.LessonConstants.ProgressStatus;
import com.education.base.common.lesson.LessonConstants.ResourceType;
import com.education.base.common.lesson.LessonErrorCodes;
import com.education.base.dto.request.PortalLessonProgressRequest;
import com.education.base.dto.response.PortalFileDto;
import com.education.base.dto.response.PortalLessonClassDto;
import com.education.base.dto.response.PortalLessonDetailDto;
import com.education.base.dto.response.PortalLessonListItemDto;
import com.education.base.dto.response.PortalLessonProgressDto;
import com.education.base.dto.response.PortalLessonResourceDto;
import com.education.base.dto.response.PortalLessonSectionDto;
import com.education.base.dto.response.PortalLessonTreeDto;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.CourseEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonProgressEntity;
import com.education.base.entity.LessonResourceEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.CourseRepository;
import com.education.base.repository.LessonProgressRepository;
import com.education.base.repository.LessonRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.impl.StudentScopeGuard;
import com.education.base.service.lesson.PortalLessonService;
import com.education.base.service.lesson.impl.LessonCatalog.Group;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Học liệu trên cổng học sinh (§3 hợp đồng GĐ3). Ngoài phạm vi / chưa mở / DRAFT -> 404 LESSON_NOT_FOUND. */
@Service
@RequiredArgsConstructor
public class PortalLessonServiceImpl implements PortalLessonService {

    static final String PORTAL_FILE_URL = "/api/v1/portal/me/files/";
    static final List<String> VISIBLE_ASSIGNMENT = List.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED);

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final ClassStudentRepository classStudentRepository;
    private final CourseRepository courseRepository;
    private final AssignmentRepository assignmentRepository;
    private final FileStorageService fileStorageService;
    private final PortalStudentContext portalStudentContext;
    private final StudentScopeGuard studentScopeGuard;
    private final AuditService auditService;
    private final LessonAccess access;
    private final LessonCatalog catalog;
    private final Clock clock;

    /** Ngữ cảnh: lớp + bài nhìn thấy (theo thứ tự cây) + bài đang xem. */
    record Ctx(ClassEntity clazz, List<LessonEntity> available, LessonEntity lesson, int index) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalLessonClassDto> listClasses() {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        LocalDateTime now = LessonRules.now(clock);
        List<ClassEntity> classes = enrolledClasses(studentId);
        Map<Long, CourseEntity> courses = new HashMap<>();
        List<Long> courseIds = classes.stream().map(ClassEntity::getCourseId).filter(Objects::nonNull).distinct().toList();
        if (!courseIds.isEmpty()) {
            courseRepository.findAllById(courseIds).forEach(c -> courses.put(c.getId(), c));
        }
        List<PortalLessonClassDto> out = new ArrayList<>();
        for (ClassEntity clazz : classes) {
            List<LessonEntity> available = catalog.availableInClass(clazz, now);
            Map<Long, LessonProgressEntity> progress = progress(studentId, available);
            int done = completed(available, progress);
            LessonEntity next = available.stream()
                    .filter(l -> !isCompleted(progress.get(l.getId()))).findFirst().orElse(null);
            CourseEntity course = clazz.getCourseId() == null ? null : courses.get(clazz.getCourseId());
            out.add(PortalLessonClassDto.builder()
                    .classId(clazz.getId())
                    .classCode(clazz.getClassCode())
                    .className(clazz.getClassName())
                    .subjectName(clazz.getSubjectName())
                    .courseName(course == null || LessonRules.deleted(course.getIsDeleted()) ? null : course.getCourseName())
                    .totalLessons(available.size())
                    .completedLessons(done)
                    .percent(LessonRules.percent(done, available.size()))
                    .continueLessonId(next == null ? null : next.getId())
                    .continueLessonTitle(next == null ? null : next.getTitle())
                    .build());
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public PortalLessonTreeDto getTree(Long classId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        studentScopeGuard.requireEnrolled(classId);
        ClassEntity clazz = access.requireClass(classId);
        LocalDateTime now = LessonRules.now(clock);
        List<Group> groups = catalog.classGroups(clazz);
        List<LessonEntity> all = LessonCatalog.flatten(groups);
        Map<Long, ClassSessionEntity> sessions = catalog.sessions(all);
        List<LessonEntity> available = all.stream()
                .filter(l -> LessonRules.availableNow(l, sessions.get(l.getSessionId()), now)).toList();
        Set<Long> availableIds = new HashSet<>();
        available.forEach(l -> availableIds.add(l.getId()));
        Map<Long, LessonProgressEntity> progress = progress(studentId, available);
        Map<Long, List<LessonResourceEntity>> resources = catalog.resources(new ArrayList<>(availableIds));
        List<PortalLessonSectionDto> sections = new ArrayList<>();
        for (Group g : groups) {
            List<PortalLessonListItemDto> items = g.lessons().stream()
                    .filter(l -> availableIds.contains(l.getId()))
                    .map(l -> {
                        ClassSessionEntity s = sessions.get(l.getSessionId());
                        LessonProgressEntity p = progress.get(l.getId());
                        List<LessonResourceEntity> res = resources.getOrDefault(l.getId(), List.of());
                        return PortalLessonListItemDto.builder()
                                .id(l.getId())
                                .title(l.getTitle())
                                .estimatedMinutes(l.getEstimatedMinutes())
                                .availableFrom(LessonRules.effectiveAvailableFrom(l, s))
                                .sessionDate(LessonRules.usableSession(s) ? s.getSessionDate() : null)
                                .progressStatus(p == null ? ProgressStatus.NOT_STARTED : p.getStatus())
                                .completedAt(p == null ? null : p.getCompletedAt())
                                .hasVideo(res.stream().anyMatch(r -> ResourceType.VIDEO.equals(r.getResourceType())))
                                .resourceCount(res.size())
                                .build();
                    })
                    .toList();
            if (!items.isEmpty()) {
                sections.add(PortalLessonSectionDto.builder()
                        .id(g.section().getId())
                        .title(g.section().getTitle())
                        .source(g.source())
                        .lessons(items)
                        .build());
            }
        }
        int done = completed(available, progress);
        return PortalLessonTreeDto.builder()
                .classId(clazz.getId())
                .className(clazz.getClassName())
                .totalLessons(available.size())
                .completedLessons(done)
                .percent(LessonRules.percent(done, available.size()))
                .sections(sections)
                .serverNow(now)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PortalLessonDetailDto get(Long lessonId, Long classId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        LocalDateTime now = LessonRules.now(clock);
        Ctx ctx = resolve(studentId, lessonId, classId, now);
        LessonEntity l = ctx.lesson();
        ClassSessionEntity session = catalog.sessions(List.of(l)).get(l.getSessionId());
        LessonProgressEntity p = progressRepository.findByLessonIdAndStudentId(l.getId(), studentId).orElse(null);
        String sectionTitle = catalog.classGroups(ctx.clazz()).stream()
                .filter(g -> Objects.equals(g.section().getId(), l.getSectionId()))
                .map(g -> g.section().getTitle()).findFirst().orElse(null);
        return PortalLessonDetailDto.builder()
                .id(l.getId())
                .classId(ctx.clazz().getId())
                .className(ctx.clazz().getClassName())
                .sectionTitle(sectionTitle)
                .title(l.getTitle())
                .content(l.getContent())
                .estimatedMinutes(l.getEstimatedMinutes())
                .availableFrom(LessonRules.effectiveAvailableFrom(l, session))
                .sessionDate(LessonRules.usableSession(session) ? session.getSessionDate() : null)
                .resources(resources(l, ctx.clazz()))
                .progress(progressDto(l.getId(), p, ctx, studentId))
                .prevLessonId(ctx.index() > 0 ? ctx.available().get(ctx.index() - 1).getId() : null)
                .nextLessonId(ctx.index() < ctx.available().size() - 1 ? ctx.available().get(ctx.index() + 1).getId() : null)
                .serverNow(now)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortalLessonProgressDto recordProgress(Long lessonId, Long classId, PortalLessonProgressRequest request) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        String status = request == null ? null : request.getStatus();
        if (!ProgressStatus.IN_PROGRESS.equals(status) && !ProgressStatus.COMPLETED.equals(status)) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Trạng thái tiến độ không hợp lệ.");
        }
        LocalDateTime now = LessonRules.now(clock);
        Ctx ctx = resolve(studentId, lessonId, classId, now);
        LessonProgressEntity p = progressRepository.findByLessonIdAndStudentId(ctx.lesson().getId(), studentId)
                .orElse(null);
        boolean newlyCompleted = false;
        if (p == null) {
            p = new LessonProgressEntity();
            p.setLessonId(ctx.lesson().getId());
            p.setStudentId(studentId);
            p.setStatus(status);
            p.setFirstViewedAt(now);
            p.setLastViewedAt(now);
            p.setViewCount(1);
            p.setCreatedAt(now);
            if (ProgressStatus.COMPLETED.equals(status)) {
                p.setCompletedAt(now);
                newlyCompleted = true;
            }
        } else {
            p.setLastViewedAt(now);
            if (ProgressStatus.IN_PROGRESS.equals(status)) {
                p.setViewCount((p.getViewCount() == null ? 0 : p.getViewCount()) + 1);
            } else if (!ProgressStatus.COMPLETED.equals(p.getStatus())) {
                p.setStatus(ProgressStatus.COMPLETED);
                newlyCompleted = true;
            }
            if (ProgressStatus.COMPLETED.equals(p.getStatus()) && p.getCompletedAt() == null) {
                p.setCompletedAt(now);
            }
        }
        LessonProgressEntity saved = progressRepository.saveAndFlush(p);
        if (newlyCompleted) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("studentId", studentId);
            d.put("classId", ctx.clazz().getId());
            auditService.success(AuditActions.LESSON_COMPLETED, AuditActions.RESOURCE_LESSON, ctx.lesson().getId(), d);
        }
        return progressDto(ctx.lesson().getId(), saved, ctx, studentId);
    }

    // ============================================================================================ nội bộ

    /**
     * Tìm lớp ngữ cảnh thấy bài. {@code classId} có: phải ENROLLED và thấy bài; NULL: lớp ENROLLED đầu tiên (id tăng)
     * thấy bài. Mọi trường hợp khác -> 404 LESSON_NOT_FOUND (không lộ tồn tại).
     */
    Ctx resolve(Long studentId, Long lessonId, Long classId, LocalDateTime now) {
        if (lessonId == null) {
            throw notFound();
        }
        LessonEntity lesson = lessonRepository.findByIdAndIsDeleted(lessonId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(PortalLessonServiceImpl::notFound);
        List<ClassEntity> candidates;
        if (classId != null) {
            try {
                studentScopeGuard.requireEnrolled(classId);
                candidates = List.of(access.requireClass(classId));
            } catch (OracleBusinessException e) {
                throw notFound();
            }
        } else {
            candidates = enrolledClasses(studentId).stream().sorted(Comparator.comparing(ClassEntity::getId)).toList();
        }
        for (ClassEntity clazz : candidates) {
            boolean owns = Objects.equals(lesson.getClassId(), clazz.getId())
                    || (lesson.getCourseId() != null && Objects.equals(lesson.getCourseId(), clazz.getCourseId()));
            if (!owns) {
                continue;
            }
            List<LessonEntity> available = catalog.availableInClass(clazz, now);
            for (int i = 0; i < available.size(); i++) {
                if (Objects.equals(available.get(i).getId(), lesson.getId())) {
                    return new Ctx(clazz, available, available.get(i), i);
                }
            }
        }
        throw notFound();
    }

    private List<ClassEntity> enrolledClasses(Long studentId) {
        Map<Long, ClassEntity> out = new LinkedHashMap<>();
        for (ClassStudentEntity cs : classStudentRepository.findActiveEnrollmentsWithClass(studentId)) {
            if (cs.getClazz() != null && !LessonRules.deleted(cs.getClazz().getIsDeleted())) {
                out.putIfAbsent(cs.getClazz().getId(), cs.getClazz());
            }
        }
        return new ArrayList<>(out.values());
    }

    private List<PortalLessonResourceDto> resources(LessonEntity l, ClassEntity clazz) {
        List<LessonResourceEntity> list = catalog.resources(List.of(l.getId())).getOrDefault(l.getId(), List.of());
        List<Long> assignmentIds = list.stream().map(LessonResourceEntity::getAssignmentId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, AssignmentEntity> assignments = new HashMap<>();
        if (!assignmentIds.isEmpty()) {
            assignmentRepository.findAllById(assignmentIds).forEach(a -> assignments.put(a.getId(), a));
        }
        List<PortalLessonResourceDto> out = new ArrayList<>();
        for (LessonResourceEntity r : list) {
            PortalLessonResourceDto.PortalLessonResourceDtoBuilder b = PortalLessonResourceDto.builder()
                    .id(r.getId()).resourceType(r.getResourceType()).title(r.getTitle());
            switch (r.getResourceType()) {
                case ResourceType.FILE -> {
                    PortalFileDto file = portalFile(r.getFileId());
                    if (file == null) {
                        continue;
                    }
                    b.file(file);
                }
                case ResourceType.VIDEO -> b.embedUrl(r.getEmbedUrl()).provider(r.getProvider());
                case ResourceType.LINK -> b.url(r.getUrl()).provider(r.getProvider());
                case ResourceType.ASSIGNMENT -> {
                    AssignmentEntity a = assignments.get(r.getAssignmentId());
                    if (a == null || LessonRules.deleted(a.getIsDeleted()) || !VISIBLE_ASSIGNMENT.contains(a.getStatus())
                            || !Objects.equals(a.getClassId(), clazz.getId())) {
                        continue;
                    }
                    b.assignmentId(a.getId()).assignmentTitle(a.getTitle()).assignmentDueAt(a.getDueAt());
                }
                default -> {
                    continue;
                }
            }
            out.add(b.build());
        }
        return out;
    }

    private PortalFileDto portalFile(Long fileId) {
        if (fileId == null) {
            return null;
        }
        FileEntity f;
        try {
            f = fileStorageService.getFileEntity(fileId);
        } catch (OracleBusinessException e) {
            return null;
        }
        if (f == null) {
            return null;
        }
        return PortalFileDto.builder()
                .id(f.getId())
                .originalName(f.getOriginalName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .fileExt(f.getFileExt())
                .viewUrl(PORTAL_FILE_URL + f.getId())
                .downloadUrl(PORTAL_FILE_URL + f.getId() + "?download=true")
                .createdAt(f.getCreatedAt())
                .canDelete(false)
                .build();
    }

    private PortalLessonProgressDto progressDto(Long lessonId, LessonProgressEntity p, Ctx ctx, Long studentId) {
        Map<Long, LessonProgressEntity> progress = progress(studentId, ctx.available());
        if (p != null) {
            progress.put(lessonId, p);
        }
        int done = completed(ctx.available(), progress);
        return PortalLessonProgressDto.builder()
                .lessonId(lessonId)
                .status(p == null ? ProgressStatus.NOT_STARTED : p.getStatus())
                .firstViewedAt(p == null ? null : p.getFirstViewedAt())
                .lastViewedAt(p == null ? null : p.getLastViewedAt())
                .completedAt(p == null ? null : p.getCompletedAt())
                .viewCount(p == null ? 0 : p.getViewCount())
                .classId(ctx.clazz().getId())
                .classCompleted(done)
                .classTotal(ctx.available().size())
                .classPercent(LessonRules.percent(done, ctx.available().size()))
                .build();
    }

    private Map<Long, LessonProgressEntity> progress(Long studentId, List<LessonEntity> lessons) {
        Map<Long, LessonProgressEntity> out = new HashMap<>();
        List<Long> ids = lessons.stream().map(LessonEntity::getId).toList();
        for (List<Long> part : LessonCatalog.chunks(ids)) {
            progressRepository.findByStudentIdAndLessonIdIn(studentId, part).forEach(p -> out.put(p.getLessonId(), p));
        }
        return out;
    }

    private static int completed(List<LessonEntity> lessons, Map<Long, LessonProgressEntity> progress) {
        return (int) lessons.stream().filter(l -> isCompleted(progress.get(l.getId()))).count();
    }

    private static boolean isCompleted(LessonProgressEntity p) {
        return p != null && ProgressStatus.COMPLETED.equals(p.getStatus());
    }

    static OracleBusinessException notFound() {
        return new OracleBusinessException(LessonErrorCodes.LESSON_NOT_FOUND, "Không tìm thấy bài học.");
    }
}
