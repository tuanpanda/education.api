package com.education.base.service.file;

import com.education.base.common.PersistenceFlags;
import com.education.base.config.FilesProperties;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.service.impl.TeachingAssignmentGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Phạm vi giáo viên với file STUDENT / CLASS (QĐ-F12): giáo viên (không phải admin) chỉ với tới lớp mình chủ nhiệm
 * hoặc có buổi dạy chưa huỷ ({@link TeachingAssignmentGuard}) và học sinh đang ghi danh (chưa xoá) ở các lớp đó.
 * Tắt khẩn bằng {@code app.files.teacher-scope-enabled=false}.
 */
@Component
@RequiredArgsConstructor
public class TeacherFileScope {

    private final TeachingAssignmentGuard teachingAssignmentGuard;
    private final ClassStudentRepository classStudentRepository;
    private final FilesProperties filesProperties;

    public boolean classAllowed(Long classId) {
        Optional<Set<Long>> taught = taught();
        return taught.isEmpty() || (classId != null && taught.get().contains(classId));
    }

    public boolean studentAllowed(Long studentId) {
        Optional<Set<Long>> taught = taught();
        if (taught.isEmpty()) {
            return true;
        }
        if (studentId == null || taught.get().isEmpty()) {
            return false;
        }
        return classStudentRepository.findByStudentIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED).stream()
                .map(ClassStudentEntity::getClassId)
                .anyMatch(taught.get()::contains);
    }

    private Optional<Set<Long>> taught() {
        if (!filesProperties.isTeacherScopeEnabled()) {
            return Optional.empty();
        }
        return teachingAssignmentGuard.taughtClassIdsIfRestricted();
    }
}
