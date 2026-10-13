package com.education.base.service.lesson.impl;

import com.education.base.common.homework.HomeworkConstants;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.CourseEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonProgressEntity;
import com.education.base.entity.LessonResourceEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.CourseRepository;
import com.education.base.repository.LessonProgressRepository;
import com.education.base.repository.LessonRepository;
import com.education.base.repository.LessonResourceRepository;
import com.education.base.repository.LessonSectionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.service.impl.TeachingAssignmentGuard;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "DB" trong bộ nhớ cho test học liệu GĐ3: các repository là mock Mockito nhưng trả lời theo dữ liệu trong map, để test
 * service với LessonCatalog / LessonAccess / TeachingAssignmentGuard thật (không Spring, không Oracle).
 */
class LessonFakeDb {

    /** "Bây giờ" của test: 13/10/2026 10:00 giờ VN. */
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 10, 0);

    final Map<Long, CourseEntity> courses = new TreeMap<>();
    final Map<Long, ClassEntity> classes = new TreeMap<>();
    final Map<Long, ClassSessionEntity> sessions = new TreeMap<>();
    final Map<Long, LessonSectionEntity> sections = new TreeMap<>();
    final Map<Long, LessonEntity> lessons = new TreeMap<>();
    final Map<Long, LessonResourceEntity> resources = new TreeMap<>();
    final Map<Long, LessonProgressEntity> progress = new TreeMap<>();
    final Map<Long, ClassStudentEntity> enrollments = new TreeMap<>();
    final Map<Long, StudentEntity> students = new TreeMap<>();
    final Map<Long, AssignmentEntity> assignments = new TreeMap<>();
    final AtomicLong seq = new AtomicLong(10_000);

    final CourseRepository courseRepository = mock(CourseRepository.class);
    final ClassRepository classRepository = mock(ClassRepository.class);
    final ClassSessionRepository sessionRepository = mock(ClassSessionRepository.class);
    final ClassStudentRepository classStudentRepository = mock(ClassStudentRepository.class);
    final StudentRepository studentRepository = mock(StudentRepository.class);
    final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    final LessonSectionRepository sectionRepository = mock(LessonSectionRepository.class);
    final LessonRepository lessonRepository = mock(LessonRepository.class);
    final LessonResourceRepository resourceRepository = mock(LessonResourceRepository.class);
    final LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);

    Clock clock = clockAt(NOW);
    final TeachingAssignmentGuard guard = new TeachingAssignmentGuard(sessionRepository, classRepository);
    final LessonAccess access = new LessonAccess(classRepository, courseRepository, guard);
    final LessonCatalog catalog = new LessonCatalog(sectionRepository, lessonRepository, resourceRepository,
            sessionRepository, courseRepository);

    static Clock clockAt(LocalDateTime vn) {
        return Clock.fixed(vn.atZone(com.education.base.common.lesson.LessonConstants.ZONE).toInstant(),
                com.education.base.common.lesson.LessonConstants.ZONE);
    }

    static boolean live(Integer d) {
        return d == null || d == 0;
    }

    LessonFakeDb() {
        // ---- course
        when(courseRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> one(courses, i.getArgument(0),
                c -> live(c.getIsDeleted())));
        when(courseRepository.existsByCourseCodeIgnoreCaseAndIsDeleted(anyString(), anyInt())).thenAnswer(i ->
                courses.values().stream().anyMatch(c -> live(c.getIsDeleted())
                        && c.getCourseCode().equalsIgnoreCase(i.getArgument(0))));
        when(courseRepository.saveAndFlush(any())).thenAnswer(i -> put(courses, i.getArgument(0), CourseEntity::getId,
                CourseEntity::setId));
        when(courseRepository.findAllById(any())).thenAnswer(i -> byIds(courses, i.getArgument(0)));
        // ---- class
        when(classRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> one(classes, i.getArgument(0),
                c -> live(c.getIsDeleted())));
        when(classRepository.saveAndFlush(any())).thenAnswer(i -> put(classes, i.getArgument(0), ClassEntity::getId,
                ClassEntity::setId));
        when(classRepository.countByCourseIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> classes.values().stream()
                .filter(c -> live(c.getIsDeleted()) && Objects.equals(c.getCourseId(), i.getArgument(0))).count());
        when(classRepository.findByCourseIdAndIsDeletedOrderByClassCode(anyLong(), anyInt())).thenAnswer(i ->
                classes.values().stream().filter(c -> live(c.getIsDeleted())
                        && Objects.equals(c.getCourseId(), i.getArgument(0))).toList());
        when(classRepository.countByCourseIds(anyCollection())).thenReturn(List.of());
        when(classRepository.findIdsByTeacherIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> classes.values()
                .stream().filter(c -> Objects.equals(c.getTeacherId(), i.getArgument(0))).map(ClassEntity::getId).toList());
        // ---- session
        when(sessionRepository.findAllById(any())).thenAnswer(i -> byIds(sessions, i.getArgument(0)));
        when(sessionRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(sessions.get(i.<Long>getArgument(0))));
        when(sessionRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> one(sessions, i.getArgument(0),
                s -> live(s.getIsDeleted())));
        // ---- enrollment / student
        when(classStudentRepository.findActiveEnrollmentsWithClass(anyLong())).thenAnswer(i -> enrollments.values()
                .stream().filter(e -> Objects.equals(e.getStudentId(), i.getArgument(0)) && live(e.getIsDeleted())
                        && "ENROLLED".equals(e.getStatus()))
                .peek(e -> e.setClazz(classes.get(e.getClassId())))
                .filter(e -> e.getClazz() != null && live(e.getClazz().getIsDeleted())).toList());
        when(classStudentRepository.findByClassIdAndStatusAndIsDeleted(anyLong(), anyString(), anyInt())).thenAnswer(i ->
                enrollments.values().stream().filter(e -> Objects.equals(e.getClassId(), i.getArgument(0))
                        && i.getArgument(1).equals(e.getStatus()) && live(e.getIsDeleted())).toList());
        when(classStudentRepository.countEnrolledByClassIds(anyCollection())).thenReturn(List.of());
        when(studentRepository.findAllById(any())).thenAnswer(i -> byIds(students, i.getArgument(0)));
        // ---- assignment
        when(assignmentRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> one(assignments,
                i.getArgument(0), a -> live(a.getIsDeleted())));
        when(assignmentRepository.findAllById(any())).thenAnswer(i -> byIds(assignments, i.getArgument(0)));
        // ---- section
        when(sectionRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> one(sections, i.getArgument(0),
                s -> live(s.getIsDeleted())));
        when(sectionRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(sections.get(i.<Long>getArgument(0))));
        when(sectionRepository.findByClassIdAndIsDeletedOrderBySortOrderAscIdAsc(anyLong(), anyInt())).thenAnswer(i ->
                sorted(sections.values(), s -> live(s.getIsDeleted()) && Objects.equals(s.getClassId(), i.getArgument(0)),
                        Comparator.comparing(LessonSectionEntity::getSortOrder).thenComparing(LessonSectionEntity::getId)));
        when(sectionRepository.findByCourseIdAndIsDeletedOrderBySortOrderAscIdAsc(anyLong(), anyInt())).thenAnswer(i ->
                sorted(sections.values(), s -> live(s.getIsDeleted()) && Objects.equals(s.getCourseId(), i.getArgument(0)),
                        Comparator.comparing(LessonSectionEntity::getSortOrder).thenComparing(LessonSectionEntity::getId)));
        when(sectionRepository.countByClassIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> sections.values().stream()
                .filter(s -> live(s.getIsDeleted()) && Objects.equals(s.getClassId(), i.getArgument(0))).count());
        when(sectionRepository.countByCourseIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> sections.values().stream()
                .filter(s -> live(s.getIsDeleted()) && Objects.equals(s.getCourseId(), i.getArgument(0))).count());
        when(sectionRepository.save(any())).thenAnswer(i -> put(sections, i.getArgument(0), LessonSectionEntity::getId,
                LessonSectionEntity::setId));
        when(sectionRepository.saveAndFlush(any())).thenAnswer(i -> put(sections, i.getArgument(0),
                LessonSectionEntity::getId, LessonSectionEntity::setId));
        // ---- lesson
        when(lessonRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> one(lessons, i.getArgument(0),
                l -> live(l.getIsDeleted())));
        when(lessonRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(lessons.get(i.<Long>getArgument(0))));
        when(lessonRepository.findAllById(any())).thenAnswer(i -> byIds(lessons, i.getArgument(0)));
        when(lessonRepository.findBySectionIdInAndIsDeletedOrderBySortOrderAscIdAsc(anyCollection(), anyInt()))
                .thenAnswer(i -> {
                    Collection<Long> ids = i.getArgument(0);
                    return sorted(lessons.values(), l -> live(l.getIsDeleted()) && ids.contains(l.getSectionId()),
                            Comparator.comparing(LessonEntity::getSortOrder).thenComparing(LessonEntity::getId));
                });
        when(lessonRepository.countBySectionIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> lessons.values().stream()
                .filter(l -> live(l.getIsDeleted()) && Objects.equals(l.getSectionId(), i.getArgument(0))).count());
        when(lessonRepository.findByCourseIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> lessons.values().stream()
                .filter(l -> live(l.getIsDeleted()) && Objects.equals(l.getCourseId(), i.getArgument(0))).toList());
        when(lessonRepository.countByCourseIds(anyCollection())).thenReturn(List.of());
        when(lessonRepository.save(any())).thenAnswer(i -> put(lessons, i.getArgument(0), LessonEntity::getId,
                LessonEntity::setId));
        when(lessonRepository.saveAndFlush(any())).thenAnswer(i -> put(lessons, i.getArgument(0), LessonEntity::getId,
                LessonEntity::setId));
        // ---- resource
        when(resourceRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> one(resources,
                i.getArgument(0), r -> live(r.getIsDeleted())));
        when(resourceRepository.findByLessonIdAndIsDeletedOrderBySortOrderAscIdAsc(anyLong(), anyInt())).thenAnswer(i ->
                sorted(resources.values(), r -> live(r.getIsDeleted()) && Objects.equals(r.getLessonId(), i.getArgument(0)),
                        Comparator.comparing(LessonResourceEntity::getSortOrder).thenComparing(LessonResourceEntity::getId)));
        when(resourceRepository.findByLessonIdInAndIsDeletedOrderBySortOrderAscIdAsc(anyCollection(), anyInt()))
                .thenAnswer(i -> {
                    Collection<Long> ids = i.getArgument(0);
                    return sorted(resources.values(), r -> live(r.getIsDeleted()) && ids.contains(r.getLessonId()),
                            Comparator.comparing(LessonResourceEntity::getSortOrder).thenComparing(LessonResourceEntity::getId));
                });
        when(resourceRepository.countByLessonIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> resources.values()
                .stream().filter(r -> live(r.getIsDeleted()) && Objects.equals(r.getLessonId(), i.getArgument(0))).count());
        when(resourceRepository.findByFileIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(i -> resources.values().stream()
                .filter(r -> live(r.getIsDeleted()) && Objects.equals(r.getFileId(), i.getArgument(0))).toList());
        when(resourceRepository.existsOtherLiveByFileId(anyLong(), anyLong())).thenAnswer(i -> resources.values().stream()
                .anyMatch(r -> live(r.getIsDeleted()) && Objects.equals(r.getFileId(), i.getArgument(0))
                        && !Objects.equals(r.getId(), i.getArgument(1))));
        when(resourceRepository.save(any())).thenAnswer(i -> put(resources, i.getArgument(0), LessonResourceEntity::getId,
                LessonResourceEntity::setId));
        when(resourceRepository.saveAndFlush(any())).thenAnswer(i -> put(resources, i.getArgument(0),
                LessonResourceEntity::getId, LessonResourceEntity::setId));
        // ---- progress
        when(progressRepository.findByLessonIdAndStudentId(anyLong(), anyLong())).thenAnswer(i -> progress.values()
                .stream().filter(p -> Objects.equals(p.getLessonId(), i.getArgument(0))
                        && Objects.equals(p.getStudentId(), i.getArgument(1))).findFirst());
        when(progressRepository.findByStudentIdAndLessonIdIn(anyLong(), anyCollection())).thenAnswer(i -> {
            Collection<Long> ids = i.getArgument(1);
            return progress.values().stream().filter(p -> Objects.equals(p.getStudentId(), i.getArgument(0))
                    && ids.contains(p.getLessonId())).toList();
        });
        when(progressRepository.findByLessonIdIn(anyCollection())).thenAnswer(i -> {
            Collection<Long> ids = i.getArgument(0);
            return progress.values().stream().filter(p -> ids.contains(p.getLessonId())).toList();
        });
        when(progressRepository.saveAndFlush(any())).thenAnswer(i -> put(progress, i.getArgument(0),
                LessonProgressEntity::getId, LessonProgressEntity::setId));
    }

    // ============================================================================================ dữ liệu

    CourseEntity course(long id, String code, String status) {
        CourseEntity c = CourseEntity.builder().id(id).courseCode(code).courseName("Khoa " + code).status(status)
                .isDeleted(0).version(0L).build();
        courses.put(id, c);
        return c;
    }

    ClassEntity clazz(long id, Long teacherId, Long courseId) {
        ClassEntity c = ClassEntity.builder().id(id).classCode("L" + id).className("Lop " + id).teacherId(teacherId)
                .courseId(courseId).status("ONGOING").isDeleted(0).build();
        classes.put(id, c);
        return c;
    }

    ClassSessionEntity session(long id, long classId, LocalDateTime start, String status) {
        ClassSessionEntity s = ClassSessionEntity.builder().id(id).classId(classId).sessionDate(start.toLocalDate())
                .startTime(String.format("%02d:%02d", start.getHour(), start.getMinute())).status(status).isDeleted(0)
                .build();
        sessions.put(id, s);
        return s;
    }

    LessonSectionEntity section(long id, Long courseId, Long classId, int order) {
        LessonSectionEntity s = LessonSectionEntity.builder().id(id).courseId(courseId).classId(classId)
                .title("Chuong " + id).sortOrder(order).isDeleted(0).version(0L).build();
        sections.put(id, s);
        return s;
    }

    /** Bài PUBLISHED mở từ hôm qua (publishedAt = NOW - 1 ngày). */
    LessonEntity lesson(long id, LessonSectionEntity s, int order) {
        LessonEntity l = LessonEntity.builder().id(id).sectionId(s.getId()).courseId(s.getCourseId())
                .classId(s.getClassId()).title("Bai " + id).sortOrder(order).status("PUBLISHED")
                .publishedAt(NOW.minusDays(1)).isDeleted(0).version(0L).build();
        lessons.put(id, l);
        return l;
    }

    LessonResourceEntity fileResource(long id, long lessonId, long fileId) {
        LessonResourceEntity r = LessonResourceEntity.builder().id(id).lessonId(lessonId).resourceType("FILE")
                .title("Tai lieu").fileId(fileId).sortOrder((int) id).isDeleted(0).build();
        resources.put(id, r);
        return r;
    }

    LessonResourceEntity videoResource(long id, long lessonId) {
        LessonResourceEntity r = LessonResourceEntity.builder().id(id).lessonId(lessonId).resourceType("VIDEO")
                .title("Video").url("https://youtu.be/dQw4w9WgXcQ")
                .embedUrl("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ").provider("YOUTUBE")
                .sortOrder((int) id).isDeleted(0).build();
        resources.put(id, r);
        return r;
    }

    void enroll(long id, long classId, long studentId) {
        enrollments.put(id, ClassStudentEntity.builder().id(id).classId(classId).studentId(studentId).status("ENROLLED")
                .isDeleted(0).build());
        students.putIfAbsent(studentId, StudentEntity.builder().id(studentId).studentCode("HS" + studentId)
                .fullName("Hoc sinh " + studentId).build());
    }

    AssignmentEntity assignment(long id, long classId, String status) {
        AssignmentEntity a = AssignmentEntity.builder().id(id).classId(classId).title("BT " + id).status(status)
                .dueAt(NOW.plusDays(3)).isDeleted(0).build();
        assignments.put(id, a);
        return a;
    }

    LessonProgressEntity progressOf(long lessonId, long studentId) {
        return progress.values().stream().filter(p -> p.getLessonId() == lessonId && p.getStudentId() == studentId)
                .findFirst().orElse(null);
    }

    static String published() {
        return HomeworkConstants.AssignmentStatus.PUBLISHED;
    }

    // ============================================================================================ tiện ích

    private static <T> Optional<T> one(Map<Long, T> map, Long id, Predicate<T> filter) {
        return Optional.ofNullable(map.get(id)).filter(filter);
    }

    private <T> T put(Map<Long, T> map, T e, Function<T, Long> getId, java.util.function.BiConsumer<T, Long> setId) {
        if (getId.apply(e) == null) {
            setId.accept(e, seq.incrementAndGet());
        }
        map.put(getId.apply(e), e);
        return e;
    }

    private static <T> List<T> byIds(Map<Long, T> map, Iterable<Long> ids) {
        List<T> out = new ArrayList<>();
        ids.forEach(id -> {
            T t = map.get(id);
            if (t != null) {
                out.add(t);
            }
        });
        return out;
    }

    private static <T> List<T> sorted(Collection<T> items, Predicate<T> filter, Comparator<T> cmp) {
        return items.stream().filter(filter).sorted(cmp).toList();
    }

}
