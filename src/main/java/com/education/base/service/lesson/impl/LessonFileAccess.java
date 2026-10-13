package com.education.base.service.lesson.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.lesson.LessonConstants.ResourceType;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonResourceEntity;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.LessonRepository;
import com.education.base.repository.LessonResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Luật đọc file module LESSON theo MỌI tài nguyên FILE chưa xoá dùng {@code fileId} (bài chép từ khoá dùng chung file).
 * Dùng cho {@code PortalFileServiceImpl} (học sinh) và {@code LessonFileModule} (nhân viên).
 */
@Component
@RequiredArgsConstructor
public class LessonFileAccess {

    private final LessonResourceRepository resourceRepository;
    private final LessonRepository lessonRepository;
    private final ClassStudentRepository classStudentRepository;
    private final LessonCatalog catalog;
    private final Clock clock;

    /** Bài chưa xoá có tài nguyên FILE chưa xoá dùng {@code fileId}. */
    @Transactional(readOnly = true)
    public List<LessonEntity> lessonsUsingFile(Long fileId) {
        if (fileId == null) {
            return List.of();
        }
        List<Long> lessonIds = resourceRepository.findByFileIdAndIsDeleted(fileId, PersistenceFlags.NOT_DELETED).stream()
                .filter(r -> ResourceType.FILE.equals(r.getResourceType()))
                .map(LessonResourceEntity::getLessonId).distinct().toList();
        List<LessonEntity> out = new ArrayList<>();
        for (List<Long> part : LessonCatalog.chunks(lessonIds)) {
            lessonRepository.findAllById(part).stream()
                    .filter(l -> !LessonRules.deleted(l.getIsDeleted()))
                    .forEach(out::add);
        }
        return out;
    }

    /**
     * Học sinh đọc được file khi có tài nguyên FILE chưa xoá dùng file này thuộc một bài học sinh đang thấy
     * (PUBLISHED, đã mở, thuộc lớp ENROLLED hoặc khoá đang gắn với lớp đó).
     */
    @Transactional(readOnly = true)
    public boolean studentCanRead(Long studentId, Long fileId) {
        if (studentId == null) {
            return false;
        }
        List<LessonEntity> lessons = lessonsUsingFile(fileId);
        if (lessons.isEmpty()) {
            return false;
        }
        Set<Long> lessonIds = new HashSet<>();
        lessons.forEach(l -> lessonIds.add(l.getId()));
        LocalDateTime now = LessonRules.now(clock);
        for (ClassStudentEntity cs : classStudentRepository.findActiveEnrollmentsWithClass(studentId)) {
            ClassEntity clazz = cs.getClazz();
            if (clazz == null) {
                continue;
            }
            boolean candidate = lessons.stream().anyMatch(l -> Objects.equals(l.getClassId(), clazz.getId())
                    || (l.getCourseId() != null && Objects.equals(l.getCourseId(), clazz.getCourseId())));
            if (candidate && catalog.availableInClass(clazz, now).stream().anyMatch(l -> lessonIds.contains(l.getId()))) {
                return true;
            }
        }
        return false;
    }
}
