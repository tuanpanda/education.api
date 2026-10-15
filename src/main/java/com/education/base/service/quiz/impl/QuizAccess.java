package com.education.base.service.quiz.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.quiz.QuizConstants.Visibility;
import com.education.base.common.quiz.QuizErrorCodes;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.QuestionEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.SecurityUtils;
import com.education.base.service.impl.TeachingAssignmentGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * GĐ4 - phạm vi nhân viên: đề theo lớp GV dạy ({@link TeachingAssignmentGuard}, 403 {@code NOT_CLASS_TEACHER});
 * câu hỏi: GV (bị giới hạn) thấy câu của mình + SHARED; sửa/xoá chỉ chủ hoặc ADMIN (403 {@code QUESTION_NOT_OWNER}).
 */
@Component
@RequiredArgsConstructor
public class QuizAccess {

    static final String MSG_NOT_CLASS_TEACHER = "Bạn không phụ trách lớp này.";
    static final String CLASS_NOT_FOUND = "CLASS_NOT_FOUND";

    private final TeachingAssignmentGuard teachingAssignmentGuard;
    private final ClassRepository classRepository;

    public Long currentUserId() {
        return SecurityUtils.currentUser().map(AuthUserPrincipal::getId).orElse(null);
    }

    public boolean isAdmin() {
        return SecurityUtils.currentUser().map(AuthUserPrincipal::isAdmin).orElse(false);
    }

    /** GV bị giới hạn phạm vi (có ROLE_TEACHER, không ADMIN). */
    public boolean restricted() {
        return teachingAssignmentGuard.isAssignmentRestricted();
    }

    /** {@code ownerId} để lọc câu nhìn thấy được: null = thấy tất cả. */
    public Long questionScopeOwnerId() {
        if (!restricted()) {
            return null;
        }
        Long id = currentUserId();
        return id == null ? -1L : id;
    }

    public boolean canSee(QuestionEntity q) {
        Long owner = questionScopeOwnerId();
        return owner == null || Objects.equals(q.getOwnerId(), owner) || Visibility.SHARED.equals(q.getVisibility());
    }

    public boolean canEdit(QuestionEntity q) {
        return isAdmin() || Objects.equals(q.getOwnerId(), currentUserId());
    }

    public void requireOwner(QuestionEntity q) {
        if (!canEdit(q)) {
            throw new ForbiddenException(QuizErrorCodes.QUESTION_NOT_OWNER, "Bạn chỉ sửa được câu hỏi của mình.");
        }
    }

    public ClassEntity requireClass(Long classId) {
        return classRepository.findByIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(CLASS_NOT_FOUND, "Không tìm thấy lớp học với ID: " + classId));
    }

    /** Lớp tồn tại + GV dạy lớp (403 NOT_CLASS_TEACHER). */
    public ClassEntity requireClassScope(Long classId) {
        ClassEntity c = requireClass(classId);
        teachingAssignmentGuard.requireCanWrite(c, MSG_NOT_CLASS_TEACHER);
        return c;
    }

    public Optional<Set<Long>> taughtClassIds() {
        return teachingAssignmentGuard.taughtClassIdsIfRestricted();
    }
}
