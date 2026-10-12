package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.security.AuthUserPrincipal;

/**
 * Module file {@code FEEDBACK}: file nhận xét của giáo viên (REFERENCE_ID = LMS_SUBMISSIONS.ID).
 * <p>
 * <b>STUB hợp đồng Luồng 0 GĐ2</b> - CHƯA là bean ({@code @Component}) nên registry vẫn fail-closed với FEEDBACK.
 * Bot 1 (feat/homework-api) viết luật thật rồi thêm {@code @Component} + test (cập nhật
 * {@code FileModuleRegistryTest.unknownModules_areRejected} / {@code FileAccessPolicyTest} /
 * {@code FileStorageServiceImplTest} đang giả định FEEDBACK chưa đăng ký).
 * <p>
 * Luật dự kiến (docs/homework-api.md mục "Luật file"): Đọc: MENU_HOMEWORK:VIEW + phạm vi lớp. Upload/xoá: MENU_HOMEWORK:GRADE + phạm vi lớp, chỉ qua API chấm bài (allowGenericUpload=false).
 */
public class FeedbackFileModule extends AbstractFileModule {

    @Override
    public String code() {
        return FileConstants.Module.FEEDBACK;
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
