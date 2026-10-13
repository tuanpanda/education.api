package com.education.base.service.lesson.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.lesson.LessonConstants.OwnerType;
import com.education.base.common.lesson.LessonErrorCodes;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.CourseEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.CourseRepository;
import com.education.base.security.Permissions;
import com.education.base.security.PermissionInterceptor;
import com.education.base.security.SecurityUtils;
import com.education.base.service.impl.TeachingAssignmentGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Kiểm quyền học liệu theo CHỦ của chương/bài (§2.2 hợp đồng).
 * <ul>
 *   <li>Chủ CLASS: MENU_LESSON:&lt;op&gt; + {@link TeachingAssignmentGuard#requireCanWrite} (GV ngoài lớp -> 403 NOT_CLASS_TEACHER).</li>
 *   <li>Chủ COURSE: đọc = COURSE_VIEW hoặc LESSON_VIEW; mọi thao tác ghi/xoá = COURSE_UPDATE.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class LessonAccess {

    public static final String MSG_NOT_CLASS_TEACHER = "Bạn không phụ trách lớp này.";
    public static final String CLASS_NOT_FOUND = "CLASS_NOT_FOUND";

    public enum Op { READ, CREATE, UPDATE, DELETE }

    /** Chủ đã nạp: đúng một trong {@code clazz} / {@code course}. */
    public record Owner(String type, Long id, ClassEntity clazz, CourseEntity course) {
        public boolean isClass() {
            return OwnerType.CLASS.equals(type);
        }
    }

    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final TeachingAssignmentGuard teachingAssignmentGuard;

    public Owner owner(String ownerType, Long ownerId) {
        String type = ownerType == null ? "" : ownerType.trim().toUpperCase(Locale.ROOT);
        if (ownerId == null || !(OwnerType.CLASS.equals(type) || OwnerType.COURSE.equals(type))) {
            throw new OracleBusinessException(LessonErrorCodes.LESSON_INVALID,
                    "Thông tin bài học không hợp lệ: thiếu hoặc sai chủ sở hữu (ownerType/ownerId).");
        }
        return OwnerType.CLASS.equals(type)
                ? new Owner(type, ownerId, requireClass(ownerId), null)
                : new Owner(type, ownerId, null, requireCourse(ownerId));
    }

    public Owner ownerOf(LessonSectionEntity s) {
        return s.getClassId() != null ? owner(OwnerType.CLASS, s.getClassId()) : owner(OwnerType.COURSE, s.getCourseId());
    }

    public Owner ownerOf(LessonEntity l) {
        return l.getClassId() != null ? owner(OwnerType.CLASS, l.getClassId()) : owner(OwnerType.COURSE, l.getCourseId());
    }

    public void require(Owner owner, Op op) {
        if (owner.isClass()) {
            String perm = switch (op) {
                case READ -> Permissions.LESSON_VIEW;
                case CREATE -> Permissions.LESSON_CREATE;
                case UPDATE -> Permissions.LESSON_UPDATE;
                case DELETE -> Permissions.LESSON_DELETE;
            };
            requireAny(List.of(perm));
            teachingAssignmentGuard.requireCanWrite(owner.clazz(), MSG_NOT_CLASS_TEACHER);
            return;
        }
        if (op == Op.READ) {
            requireAny(List.of(Permissions.COURSE_VIEW, Permissions.LESSON_VIEW));
        } else {
            requireAny(List.of(Permissions.COURSE_UPDATE));
        }
    }

    public boolean can(Owner owner, Op op) {
        try {
            require(owner, op);
            return true;
        } catch (ForbiddenException e) {
            return false;
        }
    }

    /** Phạm vi giáo viên với lớp (không kiểm mã quyền - controller đã kiểm). */
    public void requireClassScope(ClassEntity clazz) {
        teachingAssignmentGuard.requireCanWrite(clazz, MSG_NOT_CLASS_TEACHER);
    }

    public ClassEntity requireClass(Long classId) {
        if (classId == null) {
            throw classNotFound(null);
        }
        return classRepository.findByIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> classNotFound(classId));
    }

    public CourseEntity requireCourse(Long courseId) {
        if (courseId == null) {
            throw courseNotFound();
        }
        return courseRepository.findByIdAndIsDeleted(courseId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(LessonAccess::courseNotFound);
    }

    static void requireAny(List<String> permissions) {
        for (String p : permissions) {
            if (SecurityUtils.currentUserHasPermission(p)) {
                return;
            }
        }
        throw new ForbiddenException(PermissionInterceptor.FORBIDDEN_CODE, "Bạn không có quyền thực hiện chức năng này.");
    }

    static OracleBusinessException classNotFound(Long id) {
        return new OracleBusinessException(CLASS_NOT_FOUND, "Không tìm thấy lớp học với ID: " + id);
    }

    static OracleBusinessException courseNotFound() {
        return new OracleBusinessException(LessonErrorCodes.COURSE_NOT_FOUND, "Không tìm thấy khoá học.");
    }
}
