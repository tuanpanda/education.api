package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * GĐ2 - file bài làm của học sinh ({@code LMS_SUBMISSIONS.ID}). Nhân viên chỉ đọc (HOMEWORK_VIEW + phạm vi lớp của bài);
 * không upload / xoá qua API file chung. Học sinh dùng {@code /api/v1/portal/me/**}. Tính quota theo {@code STUDENT_ID}.
 */
@Component
@RequiredArgsConstructor
public class SubmissionFileModule extends AbstractFileModule {

    private final HomeworkFileRefs refs;
    private final TeacherFileScope teacherFileScope;

    @Override
    public String code() {
        return FileConstants.Module.SUBMISSION;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.HOMEWORK_VIEW))
                && teacherFileScope.classAllowed(refs.classOfSubmission(ref(file)));
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return false;
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return false;
    }

    @Override
    public boolean allowGenericUpload() {
        return false;
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        SubmissionEntity s = refs.submission(referenceId).orElseThrow(() -> referenceNotFound(code(), referenceId));
        if (HomeworkFileRefs.deleted(s.getIsDeleted())) {
            throw referenceInactive(code(), referenceId);
        }
    }

    @Override
    public FileReferenceInfo describeReference(Long referenceId) {
        if (referenceId == null) {
            return FileReferenceInfo.none(code());
        }
        return refs.submission(referenceId)
                .map(s -> new FileReferenceInfo(code(), referenceId,
                        "Bài nộp: " + refs.assignment(s.getAssignmentId()).map(a -> a.getTitle()).orElse("?")
                                + " (HS " + s.getStudentId() + ")",
                        "/academic/homework?id=" + s.getAssignmentId() + "&submission=" + referenceId,
                        HomeworkFileRefs.deleted(s.getIsDeleted())
                                ? FileConstants.RefState.DELETED : FileConstants.RefState.ACTIVE))
                .orElseGet(() -> FileReferenceInfo.notFound(code(), referenceId));
    }

    @Override
    public Optional<Long> ownerStudentId(Long referenceId) {
        return refs.submission(referenceId).map(SubmissionEntity::getStudentId);
    }
}
