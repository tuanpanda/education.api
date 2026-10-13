package com.education.base.service.lesson.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.lesson.LessonConstants.OwnerType;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonResourceEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.CourseRepository;
import com.education.base.repository.LessonRepository;
import com.education.base.repository.LessonResourceRepository;
import com.education.base.repository.LessonSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Nạp cây chương/bài theo lô (IN chia lô &lt;= 900, không gọi theo từng dòng). Dùng chung cho staff, cổng và luật file.
 * Thứ tự cây của một lớp = chương khoá gắn (trước) + chương riêng của lớp (sau), mỗi nhóm theo SORT_ORDER, ID.
 */
@Component
@RequiredArgsConstructor
public class LessonCatalog {

    static final int CHUNK = 900;

    /** Một chương và các bài chưa xoá của nó (đã sắp). {@code source} = COURSE / CLASS. */
    public record Group(LessonSectionEntity section, String source, List<LessonEntity> lessons) {
    }

    private final LessonSectionRepository sectionRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository resourceRepository;
    private final ClassSessionRepository sessionRepository;
    private final CourseRepository courseRepository;

    public List<Group> ownerGroups(String ownerType, Long ownerId) {
        List<LessonSectionEntity> sections = OwnerType.CLASS.equals(ownerType)
                ? sectionRepository.findByClassIdAndIsDeletedOrderBySortOrderAscIdAsc(ownerId, PersistenceFlags.NOT_DELETED)
                : sectionRepository.findByCourseIdAndIsDeletedOrderBySortOrderAscIdAsc(ownerId, PersistenceFlags.NOT_DELETED);
        return groups(sections, ownerType);
    }

    /** Chương của khoá đang gắn với lớp (khoá đã xoá -> rỗng). */
    public List<Group> inheritedGroups(ClassEntity clazz) {
        if (clazz.getCourseId() == null
                || courseRepository.findByIdAndIsDeleted(clazz.getCourseId(), PersistenceFlags.NOT_DELETED).isEmpty()) {
            return List.of();
        }
        return ownerGroups(OwnerType.COURSE, clazz.getCourseId());
    }

    /** Toàn bộ cây lớp (khoá trước, lớp sau). */
    public List<Group> classGroups(ClassEntity clazz) {
        List<Group> out = new ArrayList<>(inheritedGroups(clazz));
        out.addAll(ownerGroups(OwnerType.CLASS, clazz.getId()));
        return out;
    }

    /** Bài {@code availableNow} của lớp theo thứ tự cây. */
    public List<LessonEntity> availableInClass(ClassEntity clazz, LocalDateTime now) {
        List<Group> groups = classGroups(clazz);
        List<LessonEntity> all = flatten(groups);
        Map<Long, ClassSessionEntity> sessions = sessions(all);
        return all.stream().filter(l -> LessonRules.availableNow(l, sessions.get(l.getSessionId()), now)).toList();
    }

    public List<Group> groups(List<LessonSectionEntity> sections, String source) {
        if (sections.isEmpty()) {
            return List.of();
        }
        List<Long> ids = sections.stream().map(LessonSectionEntity::getId).toList();
        Map<Long, List<LessonEntity>> bySection = new HashMap<>();
        for (List<Long> part : chunks(ids)) {
            for (LessonEntity l : lessonRepository.findBySectionIdInAndIsDeletedOrderBySortOrderAscIdAsc(part,
                    PersistenceFlags.NOT_DELETED)) {
                bySection.computeIfAbsent(l.getSectionId(), k -> new ArrayList<>()).add(l);
            }
        }
        List<Group> out = new ArrayList<>();
        for (LessonSectionEntity s : sections) {
            out.add(new Group(s, source, bySection.getOrDefault(s.getId(), List.of())));
        }
        return out;
    }

    public static List<LessonEntity> flatten(List<Group> groups) {
        List<LessonEntity> out = new ArrayList<>();
        groups.forEach(g -> out.addAll(g.lessons()));
        return out;
    }

    public Map<Long, ClassSessionEntity> sessions(Collection<LessonEntity> lessons) {
        List<Long> ids = lessons.stream().map(LessonEntity::getSessionId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ClassSessionEntity> out = new HashMap<>();
        for (List<Long> part : chunks(ids)) {
            sessionRepository.findAllById(part).forEach(s -> out.put(s.getId(), s));
        }
        return out;
    }

    /** Tài nguyên chưa xoá theo bài (đã sắp). */
    public Map<Long, List<LessonResourceEntity>> resources(Collection<Long> lessonIds) {
        Map<Long, List<LessonResourceEntity>> out = new LinkedHashMap<>();
        for (List<Long> part : chunks(lessonIds.stream().distinct().toList())) {
            for (LessonResourceEntity r : resourceRepository.findByLessonIdInAndIsDeletedOrderBySortOrderAscIdAsc(part,
                    PersistenceFlags.NOT_DELETED)) {
                out.computeIfAbsent(r.getLessonId(), k -> new ArrayList<>()).add(r);
            }
        }
        return out;
    }

    public static <T> List<List<T>> chunks(List<T> ids) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += CHUNK) {
            out.add(ids.subList(i, Math.min(i + CHUNK, ids.size())));
        }
        return out;
    }

    public static <T> Map<Long, T> index(Collection<T> items, Function<T, Long> key) {
        Map<Long, T> out = new HashMap<>();
        items.forEach(i -> out.put(key.apply(i), i));
        return out;
    }
}
