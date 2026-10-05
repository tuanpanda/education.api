package com.education.base.service.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.entity.ClassEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Giới hạn ghi dữ liệu học vụ (điểm, điểm danh, thông báo lớp) của giảng viên theo lớp được phân công.
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
    private final ClassRepository classRepository;

    /**
     * {@code true} khi người dùng hiện tại là giảng viên bị giới hạn phân công
     * (có {@link Permissions#TEACHER_ROLE} và không phải admin).
     */
    public boolean isAssignmentRestricted() {
        AuthUserPrincipal principal = SecurityUtils.currentUser().orElse(null);
        return principal != null
                && !principal.isAdmin()
                && principal.getRoles() != null
                && principal.getRoles().contains(Permissions.TEACHER_ROLE);
    }

    /**
     * Ném 403 {@code NOT_CLASS_TEACHER} nếu người dùng hiện tại là giảng viên nhưng không dạy lớp {@code clazz}.
     */
    public void requireCanWrite(ClassEntity clazz, String message) {
        if (!isAssignmentRestricted()) {
            return;
        }
        Long userId = SecurityUtils.currentUser().map(AuthUserPrincipal::getId).orElse(null);
        if (userId != null && Objects.equals(clazz.getTeacherId(), userId)) {
            return;
        }
        if (userId != null && classSessionRepository.existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(
                clazz.getId(), userId, PersistenceFlags.NOT_DELETED, SESSION_STATUS_CANCELLED)) {
            return;
        }
        throw new ForbiddenException(NOT_CLASS_TEACHER_CODE, message);
    }

    /**
     * Tập ID lớp giảng viên hiện tại được phép ghi. {@link Optional#empty()} nếu không bị giới hạn
     * (admin / giáo vụ / chưa đăng nhập theo quy ước hiện tại của {@link #requireCanWrite}).
     */
    public Optional<Set<Long>> taughtClassIdsIfRestricted() {
        if (!isAssignmentRestricted()) {
            return Optional.empty();
        }
        Long userId = SecurityUtils.currentUser().map(AuthUserPrincipal::getId).orElse(null);
        if (userId == null) {
            return Optional.of(Set.of());
        }
        Set<Long> ids = new HashSet<>(classRepository.findIdsByTeacherIdAndIsDeleted(
                userId, PersistenceFlags.NOT_DELETED));
        ids.addAll(classSessionRepository.findDistinctClassIdsByTeacherIdAndIsDeletedAndStatusNot(
                userId, PersistenceFlags.NOT_DELETED, SESSION_STATUS_CANCELLED));
        return Optional.of(ids);
    }

    /**
     * Lớp giảng viên hiện tại được phép quản lý thông báo theo lớp (chủ nhiệm hoặc có buổi dạy).
     * Rỗng nếu không bị giới hạn — caller dùng danh sách lớp đầy đủ (searchClasses).
     */
    public Optional<List<ClassEntity>> taughtClassesIfRestricted() {
        Optional<Set<Long>> idsOpt = taughtClassIdsIfRestricted();
        if (idsOpt.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> ids = idsOpt.get();
        if (ids.isEmpty()) {
            return Optional.of(List.of());
        }
        return Optional.of(classRepository.findByIdInAndIsDeletedOrderByClassCode(
                ids, PersistenceFlags.NOT_DELETED));
    }
}