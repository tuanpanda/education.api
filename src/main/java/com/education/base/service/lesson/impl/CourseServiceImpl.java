package com.education.base.service.lesson.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.lesson.LessonConstants;
import com.education.base.common.lesson.LessonConstants.CourseStatus;
import com.education.base.common.lesson.LessonConstants.LessonStatus;
import com.education.base.common.lesson.LessonConstants.OwnerType;
import com.education.base.common.lesson.LessonConstants.ResourceType;
import com.education.base.common.lesson.LessonErrorCodes;
import com.education.base.dto.request.CourseFilterRequest;
import com.education.base.dto.request.CourseUpsertRequest;
import com.education.base.dto.response.CourseClassDto;
import com.education.base.dto.response.CourseCopyResultDto;
import com.education.base.dto.response.CourseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.CourseEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonResourceEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.CourseRepository;
import com.education.base.repository.LessonRepository;
import com.education.base.repository.LessonResourceRepository;
import com.education.base.repository.LessonSectionRepository;
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.lesson.CourseService;
import com.education.base.service.lesson.impl.LessonCatalog.Group;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Khoá học mẫu + liên kết sống với lớp + chép sang lớp (§2.1 hợp đồng GĐ3). */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    static final int MAX_PAGE_SIZE = 100;

    private final CourseRepository courseRepository;
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final LessonSectionRepository sectionRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository resourceRepository;
    private final HtmlContentSanitizer htmlContentSanitizer;
    private final FileLifecycleService fileLifecycleService;
    private final AuditService auditService;
    private final LessonAccess access;
    private final LessonCatalog catalog;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseDto> search(CourseFilterRequest filter) {
        CourseFilterRequest f = filter == null ? new CourseFilterRequest() : filter;
        int pageNo = Math.max(f.getPageNo() == null ? 1 : f.getPageNo(), 1);
        int pageSize = Math.min(Math.max(f.getPageSize() == null ? 20 : f.getPageSize(), 1), MAX_PAGE_SIZE);
        Specification<CourseEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            if (f.getStatus() != null && !f.getStatus().isBlank()) {
                p.add(cb.equal(root.get("status"), f.getStatus().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.getKeyword() != null && !f.getKeyword().isBlank()) {
                String kw = "%" + f.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("courseName")), kw),
                        cb.like(cb.lower(root.get("courseCode")), kw),
                        cb.like(cb.lower(root.get("subjectName")), kw)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<CourseEntity> page = courseRepository.findAll(spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.asc("courseName"), Sort.Order.asc("id"))));
        List<Long> ids = page.getContent().stream().map(CourseEntity::getId).toList();
        Map<Long, Integer> classCounts = new HashMap<>();
        Map<Long, int[]> lessonCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : classRepository.countByCourseIds(ids)) {
                classCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
            }
            for (Object[] row : lessonRepository.countByCourseIds(ids)) {
                lessonCounts.put(((Number) row[0]).longValue(), new int[]{((Number) row[1]).intValue(),
                        row[2] == null ? 0 : ((Number) row[2]).intValue()});
            }
        }
        List<CourseDto> content = page.getContent().stream()
                .map(c -> toDto(c, false, classCounts.getOrDefault(c.getId(), 0),
                        lessonCounts.getOrDefault(c.getId(), new int[]{0, 0})))
                .toList();
        return PageResponse.of(content, pageNo, pageSize, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDto getById(Long id) {
        return fullDto(access.requireCourse(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseDto create(CourseUpsertRequest request) {
        requirePayload(request);
        String code = request.getCourseCode().trim();
        if (courseRepository.existsByCourseCodeIgnoreCaseAndIsDeleted(code, PersistenceFlags.NOT_DELETED)) {
            throw duplicate();
        }
        CourseEntity c = new CourseEntity();
        c.setCourseCode(code);
        apply(c, request);
        if (c.getStatus() == null) {
            c.setStatus(CourseStatus.ACTIVE);
        }
        c.setIsDeleted(PersistenceFlags.NOT_DELETED);
        c.setCreatedAt(LessonRules.now(clock));
        c.setCreatedBy(SecurityUtils.currentUsername());
        CourseEntity saved = courseRepository.saveAndFlush(c);
        audit(AuditActions.LESSON_COURSE_CREATED, saved, Map.of());
        return fullDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseDto update(Long id, CourseUpsertRequest request) {
        requirePayload(request);
        CourseEntity c = access.requireCourse(id);
        String code = request.getCourseCode().trim();
        if (!code.equalsIgnoreCase(c.getCourseCode())
                && courseRepository.existsByCourseCodeIgnoreCaseAndIsDeleted(code, PersistenceFlags.NOT_DELETED)) {
            throw duplicate();
        }
        c.setCourseCode(code);
        apply(c, request);
        c.setUpdatedBy(SecurityUtils.currentUsername());
        CourseEntity saved = courseRepository.saveAndFlush(c);
        audit(AuditActions.LESSON_COURSE_UPDATED, saved, Map.of());
        return fullDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        CourseEntity c = access.requireCourse(id);
        if (classRepository.countByCourseIdAndIsDeleted(c.getId(), PersistenceFlags.NOT_DELETED) > 0) {
            throw new OracleBusinessException(LessonErrorCodes.COURSE_IN_USE,
                    "Khoá học đang được lớp sử dụng, hãy gỡ lớp hoặc lưu trữ khoá.");
        }
        String by = SecurityUtils.currentUsername();
        List<LessonEntity> lessons = lessonRepository.findByCourseIdAndIsDeleted(c.getId(), PersistenceFlags.NOT_DELETED);
        catalog.resources(lessons.stream().map(LessonEntity::getId).toList()).values().forEach(list -> list.forEach(r -> {
            // FILE: chỉ xoá mềm file khi không còn tài nguyên chưa xoá khác (bản chép ở lớp) dùng chung FILE_ID.
            r.setIsDeleted(PersistenceFlags.DELETED);
            r.setUpdatedBy(by);
            resourceRepository.saveAndFlush(r);
            if (ResourceType.FILE.equals(r.getResourceType()) && r.getFileId() != null
                    && !resourceRepository.existsOtherLiveByFileId(r.getFileId(), r.getId())) {
                try {
                    fileLifecycleService.softDelete(r.getFileId(), "Xoá khoá học");
                } catch (OracleBusinessException ignored) {
                    // file đã xoá: bỏ qua
                }
            }
        }));
        lessons.forEach(l -> {
            l.setIsDeleted(PersistenceFlags.DELETED);
            l.setUpdatedBy(by);
            lessonRepository.save(l);
        });
        sectionRepository.findByCourseIdAndIsDeletedOrderBySortOrderAscIdAsc(c.getId(), PersistenceFlags.NOT_DELETED)
                .forEach(s -> {
                    s.setIsDeleted(PersistenceFlags.DELETED);
                    s.setUpdatedBy(by);
                    sectionRepository.save(s);
                });
        c.setIsDeleted(PersistenceFlags.DELETED);
        c.setUpdatedBy(by);
        courseRepository.saveAndFlush(c);
        audit(AuditActions.LESSON_COURSE_DELETED, c, Map.of("lessonCount", lessons.size()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseClassDto> listClasses(Long id) {
        CourseEntity c = access.requireCourse(id);
        List<ClassEntity> classes = classRepository.findByCourseIdAndIsDeletedOrderByClassCode(c.getId(),
                PersistenceFlags.NOT_DELETED);
        Map<Long, Integer> counts = enrolledCounts(classes.stream().map(ClassEntity::getId).toList());
        return classes.stream().map(cl -> toClassDto(cl, counts.getOrDefault(cl.getId(), 0))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseClassDto linkClass(Long id, Long classId) {
        CourseEntity c = access.requireCourse(id);
        ClassEntity clazz = access.requireClass(classId);
        if (Objects.equals(clazz.getCourseId(), c.getId())) {
            return toClassDto(clazz, enrolledCounts(List.of(clazz.getId())).getOrDefault(clazz.getId(), 0));
        }
        if (CourseStatus.ARCHIVED.equals(c.getStatus()) || clazz.getCourseId() != null) {
            throw invalidState();
        }
        clazz.setCourseId(c.getId());
        clazz.setUpdatedBy(SecurityUtils.currentUsername());
        classRepository.saveAndFlush(clazz);
        audit(AuditActions.LESSON_COURSE_LINKED, c, Map.of("classId", clazz.getId()));
        return toClassDto(clazz, enrolledCounts(List.of(clazz.getId())).getOrDefault(clazz.getId(), 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkClass(Long id, Long classId) {
        CourseEntity c = access.requireCourse(id);
        ClassEntity clazz = access.requireClass(classId);
        if (!Objects.equals(clazz.getCourseId(), c.getId())) {
            throw invalidState();
        }
        clazz.setCourseId(null);
        clazz.setUpdatedBy(SecurityUtils.currentUsername());
        classRepository.saveAndFlush(clazz);
        audit(AuditActions.LESSON_COURSE_UNLINKED, c, Map.of("classId", clazz.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseCopyResultDto copyToClass(Long id, Long classId) {
        CourseEntity c = access.requireCourse(id);
        ClassEntity clazz = access.requireClass(classId);
        access.requireClassScope(clazz);
        List<Group> groups = catalog.ownerGroups(OwnerType.COURSE, c.getId());
        long existing = sectionRepository.countByClassIdAndIsDeleted(clazz.getId(), PersistenceFlags.NOT_DELETED);
        if (existing + groups.size() > LessonConstants.MAX_SECTIONS_PER_OWNER) {
            throw new OracleBusinessException(LessonErrorCodes.LESSON_INVALID,
                    "Thông tin bài học không hợp lệ: lớp sẽ vượt quá " + LessonConstants.MAX_SECTIONS_PER_OWNER
                            + " chương.");
        }
        Map<Long, List<LessonResourceEntity>> resources = catalog.resources(
                LessonCatalog.flatten(groups).stream().map(LessonEntity::getId).toList());
        String by = SecurityUtils.currentUsername();
        LocalDateTime now = LessonRules.now(clock);
        int sections = 0;
        int lessons = 0;
        int resourceCount = 0;
        int order = (int) existing;
        for (Group g : groups) {
            LessonSectionEntity src = g.section();
            LessonSectionEntity s = new LessonSectionEntity();
            s.setClassId(clazz.getId());
            s.setTitle(src.getTitle());
            s.setSortOrder(order++);
            s.setSourceSectionId(src.getId());
            s.setIsDeleted(PersistenceFlags.NOT_DELETED);
            s.setCreatedAt(now);
            s.setCreatedBy(by);
            LessonSectionEntity savedSection = sectionRepository.save(s);
            sections++;
            for (LessonEntity srcLesson : g.lessons()) {
                LessonEntity l = new LessonEntity();
                l.setSectionId(savedSection.getId());
                l.setClassId(clazz.getId());
                l.setTitle(srcLesson.getTitle());
                l.setContent(srcLesson.getContent());
                l.setSortOrder(srcLesson.getSortOrder());
                l.setStatus(LessonStatus.DRAFT);
                l.setAvailableFrom(srcLesson.getAvailableFrom());
                l.setEstimatedMinutes(srcLesson.getEstimatedMinutes());
                l.setSourceLessonId(srcLesson.getId());
                l.setIsDeleted(PersistenceFlags.NOT_DELETED);
                l.setCreatedAt(now);
                l.setCreatedBy(by);
                LessonEntity savedLesson = lessonRepository.save(l);
                lessons++;
                for (LessonResourceEntity srcRes : resources.getOrDefault(srcLesson.getId(), List.of())) {
                    if (ResourceType.ASSIGNMENT.equals(srcRes.getResourceType())) {
                        continue;
                    }
                    LessonResourceEntity r = new LessonResourceEntity();
                    r.setLessonId(savedLesson.getId());
                    r.setResourceType(srcRes.getResourceType());
                    r.setTitle(srcRes.getTitle());
                    r.setFileId(srcRes.getFileId());
                    r.setUrl(srcRes.getUrl());
                    r.setEmbedUrl(srcRes.getEmbedUrl());
                    r.setProvider(srcRes.getProvider());
                    r.setSortOrder(srcRes.getSortOrder());
                    r.setIsDeleted(PersistenceFlags.NOT_DELETED);
                    r.setCreatedAt(now);
                    r.setCreatedBy(by);
                    resourceRepository.save(r);
                    resourceCount++;
                }
            }
        }
        sectionRepository.flush();
        lessonRepository.flush();
        resourceRepository.flush();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("classId", clazz.getId());
        d.put("sections", sections);
        d.put("lessons", lessons);
        d.put("resources", resourceCount);
        audit(AuditActions.LESSON_COURSE_COPIED, c, d);
        return CourseCopyResultDto.builder()
                .courseId(c.getId())
                .classId(clazz.getId())
                .sectionCount(sections)
                .lessonCount(lessons)
                .resourceCount(resourceCount)
                .build();
    }

    // ============================================================================================ nội bộ

    private CourseDto fullDto(CourseEntity c) {
        int classCount = (int) classRepository.countByCourseIdAndIsDeleted(c.getId(), PersistenceFlags.NOT_DELETED);
        int[] lc = new int[]{0, 0};
        for (Object[] row : lessonRepository.countByCourseIds(List.of(c.getId()))) {
            lc = new int[]{((Number) row[1]).intValue(), row[2] == null ? 0 : ((Number) row[2]).intValue()};
        }
        return toDto(c, true, classCount, lc);
    }

    private static CourseDto toDto(CourseEntity c, boolean full, int classCount, int[] lessonCounts) {
        return CourseDto.builder()
                .id(c.getId())
                .courseCode(c.getCourseCode())
                .courseName(c.getCourseName())
                .subjectName(c.getSubjectName())
                .gradeLevel(c.getGradeLevel())
                .description(full ? c.getDescription() : null)
                .status(c.getStatus())
                .classCount(classCount)
                .lessonCount(lessonCounts[0])
                .publishedLessonCount(lessonCounts[1])
                .version(c.getVersion())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .createdBy(c.getCreatedBy())
                .updatedBy(c.getUpdatedBy())
                .canManage(SecurityUtils.currentUserHasPermission(Permissions.COURSE_UPDATE))
                .build();
    }

    private static CourseClassDto toClassDto(ClassEntity c, int studentCount) {
        return CourseClassDto.builder()
                .classId(c.getId())
                .classCode(c.getClassCode())
                .className(c.getClassName())
                .status(c.getStatus())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .studentCount(studentCount)
                .build();
    }

    private Map<Long, Integer> enrolledCounts(List<Long> classIds) {
        Map<Long, Integer> out = new HashMap<>();
        for (List<Long> part : LessonCatalog.chunks(classIds)) {
            for (Object[] row : classStudentRepository.countEnrolledByClassIds(part)) {
                out.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
            }
        }
        return out;
    }

    private void apply(CourseEntity c, CourseUpsertRequest r) {
        c.setCourseName(r.getCourseName().strip());
        c.setSubjectName(r.getSubjectName() == null || r.getSubjectName().isBlank() ? null : r.getSubjectName().strip());
        c.setGradeLevel(r.getGradeLevel());
        String desc = r.getDescription() == null || r.getDescription().isBlank() ? null
                : htmlContentSanitizer.sanitize(r.getDescription());
        c.setDescription(desc == null || desc.isBlank() ? null : desc);
        if (r.getStatus() != null && !r.getStatus().isBlank()) {
            c.setStatus(r.getStatus().trim().toUpperCase(Locale.ROOT));
        }
    }

    private static void requirePayload(CourseUpsertRequest r) {
        if (r == null || r.getCourseCode() == null || r.getCourseCode().isBlank()
                || r.getCourseName() == null || r.getCourseName().isBlank()) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Thiếu mã hoặc tên khoá học.");
        }
    }

    private void audit(String action, CourseEntity c, Map<String, ?> extra) {
        Map<String, Object> d = new LinkedHashMap<>(extra);
        d.put("code", c.getCourseCode());
        d.put("status", c.getStatus());
        auditService.success(action, AuditActions.RESOURCE_COURSE, c.getId(), d);
    }

    private static OracleBusinessException duplicate() {
        return new OracleBusinessException(LessonErrorCodes.COURSE_CODE_DUPLICATE, "Mã khoá học đã tồn tại.");
    }

    private static OracleBusinessException invalidState() {
        return new OracleBusinessException(LessonErrorCodes.COURSE_INVALID_STATE, "Không thể gắn lớp với khoá học này.");
    }
}
