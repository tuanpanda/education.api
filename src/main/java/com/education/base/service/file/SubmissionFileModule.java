package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.security.AuthUserPrincipal;

/**
 * Module file {@code SUBMISSION}: bài làm của học sinh (REFERENCE_ID = LMS_SUBMISSIONS.ID).
 * <p>
 * <b>STUB hợp đồng Luồng 0 GĐ2</b> - CHƯA là bean ({@code @Component}) nên registry vẫn fail-closed với SUBMISSION.
 * Bot 1 (feat/homework-api) viết luật thật rồi thêm {@code @Component} + test (cập nhật
 * {@code FileModuleRegistryTest.unknownModules_areRejected} / {@code FileAccessPolicyTest} /
 * {@code FileStorageServiceImplTest} đang giả định SUBMISSION chưa đăng ký).
 * <p>
 * Luật dự kiến (docs/homework-api.md mục "Luật file"): Đọc: MENU_HOMEWORK:VIEW + phạm vi lớp của bài. Upload: chỉ qua API cổng (allowGenericUpload=false). Xoá qua /api/v1/files: KHÔNG (false) - học sinh xoá qua API cổng. ownerStudentId = LMS_SUBMISSIONS.STUDENT_ID (tính quota).
 */
public class SubmissionFileModule extends AbstractFileModule {

    @Override
    public String code() {
        return FileConstants.Module.SUBMISSION;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return false;
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
        throw referenceNotFound(code(), referenceId);
    }

    @Override
    public FileReferenceInfo describeReference(Long referenceId) {
        return referenceId == null ? FileReferenceInfo.none(code()) : FileReferenceInfo.notFound(code(), referenceId);
    }
}
