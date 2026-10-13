package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.security.AuthUserPrincipal;

/**
 * Module file {@code LESSON}: tài liệu bài học (REFERENCE_ID = LMS_LESSONS.ID nơi file được tải lên; quyền đọc tính theo
 * MỌI dòng LMS_LESSON_RESOURCES có FILE_ID này, vì bài chép từ khoá mẫu dùng chung file).
 * <p>
 * <b>STUB hợp đồng Luồng 0 GĐ3</b> - CHƯA là bean ({@code @Component}) nên registry vẫn fail-closed với LESSON (V21_4 bật
 * module nhưng chưa có descriptor -> mọi thao tác file LESSON bị từ chối). Bot 1 (feat/portal-p3-api) viết luật thật,
 * thêm {@code @Component} và sửa {@code LessonContractTest.lessonDescriptorIsStubNotBean}.
 * <p>
 * Luật dự kiến (docs/portal-p3-api.md §4):
 * Đọc: MENU_LESSON:VIEW hoặc MENU_COURSE:VIEW; nếu mọi bài dùng file đều thuộc lớp thì cần phạm vi giáo viên ở ít nhất một lớp.
 * Upload/xoá: chỉ qua API học liệu (allowGenericUpload=false, canUpload/canDelete qua /api/v1/files = false).
 * assertReferenceUsable: bài tồn tại, chưa xoá. describeReference: "Bài học: <tiêu đề>", route /academic/lessons?lesson=<id>.
 */
public class LessonFileModule extends AbstractFileModule {

    @Override
    public String code() {
        return FileConstants.Module.LESSON;
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
