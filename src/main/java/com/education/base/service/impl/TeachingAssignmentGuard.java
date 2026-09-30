package com.education.base.service.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.entity.ClassEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Giới hạn ghi dữ liệu học vụ (điểm, điểm danh) của giảng viên theo lớp được phân công.
 * <p>
 * Giảng viên là tài khoản {@code SYS_USERS}: {@code EDU_CLASSES.TEACHER_ID} (giảng viên chủ nhiệm) và
 * {@code EDU_CLASS_SESSIONS.TEACHER_ID} (giảng viên từng buổi) đều tham chiếu {@code SYS_USERS.ID}.
 * Người dùng có vai trò {@link Permissions#TEACHER_ROLE} và không phải {@link Permissions#ADMIN_ROLE}
 * chỉ được ghi cho lớp mình chủ nhiệm hoặc dạy ít nhất một buổi (chưa xóa, chưa hủy).
 * Người dùng khác (giáo vụ, quản lý...) giữ nguyên phạm vi theo quyền menu.
 */
@Component
@RequiredArgsConstructor
public class TeachingAssignmentGuard {

    static final String NOT_CLASS_TEACHER_CODE = "NOT_CLASS_TEACHER";

    private static final String SESSION_STATUS_CANCELLED = "CANCELLED";

    private final ClassSessionRepository classSessionRepository;

    /**
     * Ném 403 {@code NOT_CLASS_TEACHER} nếu người dùng hiện tại là giảng viên nhưng không dạy lớp {@code clazz}.
     */
    public void requireCanWrite(ClassEntity clazz, String message) {
        AuthUserPrincipal principal = SecurityUtils.currentUser().orElse(null);
        if (principal == null || principal.isAdmin()
                || principal.getRoles() == null || !principal.getRoles().contains(Permissions.TEACHER_ROLE)) {
            return;
        }
        Long userId = principal.getId();
        if (userId != null && Objects.equals(clazz.getTeacherId(), userId)) {
            return;
        }
        if (userId != null && classSessionRepository.existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(
                clazz.getId(), userId, PersistenceFlags.NOT_DELETED, SESSION_STATUS_CANCELLED)) {
            return;
        }
        throw new ForbiddenException(NOT_CLASS_TEACHER_CODE, message);
    }
}
