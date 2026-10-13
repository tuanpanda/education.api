package com.education.base.service.file;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.repository.ClassRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GĐ2 - file đề bài ({@code LMS_ASSIGNMENTS.ID}). Đọc = HOMEWORK_VIEW + phạm vi lớp; upload/xoá = HOMEWORK_UPDATE|CREATE
 * + phạm vi. Không upload qua API file chung (chỉ {@code /api/v1/lms/assignments/{id}/attachments}).
 */
@Component
@RequiredArgsConstructor
public class AssignmentFileModule extends AbstractFileModule {

    private final HomeworkFileRefs refs;
    private final ClassRepository classRepository;
    private final TeacherFileScope teacherFileScope;

    @Override
    public String code() {
        return FileConstants.Module.ASSIGNMENT;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.HOMEWORK_VIEW))
                && teacherFileScope.classAllowed(refs.classOfAssignment(ref(file)));
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return any(user, List.of(Permissions.HOMEWORK_UPDATE, Permissions.HOMEWORK_CREATE))
                && teacherFileScope.classAllowed(refs.classOfAssignment(referenceId));
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.HOMEWORK_UPDATE, Permissions.HOMEWORK_CREATE))
                && teacherFileScope.classAllowed(refs.classOfAssignment(ref(file)));
    }

    @Override
    public boolean allowGenericUpload() {
        return false;
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        AssignmentEntity a = refs.assignment(referenceId).orElseThrow(() -> referenceNotFound(code(), referenceId));
        if (HomeworkFileRefs.deleted(a.getIsDeleted())) {
            throw referenceInactive(code(), referenceId);
        }
    }

    @Override
    public FileReferenceInfo describeReference(Long referenceId) {
        if (referenceId == null) {
            return FileReferenceInfo.none(code());
        }
        return refs.assignment(referenceId)
                .map(a -> new FileReferenceInfo(code(), referenceId,
                        "Bài tập: " + a.getTitle() + " (" + classCode(a.getClassId()) + ")",
                        "/academic/homework?id=" + referenceId,
                        HomeworkFileRefs.deleted(a.getIsDeleted())
                                ? FileConstants.RefState.DELETED : FileConstants.RefState.ACTIVE))
                .orElseGet(() -> FileReferenceInfo.notFound(code(), referenceId));
    }

    private String classCode(Long classId) {
        if (classId == null) {
            return "?";
        }
        return classRepository.findByIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED)
                .map(c -> c.getClassCode()).orElse("lớp " + classId);
    }
}
