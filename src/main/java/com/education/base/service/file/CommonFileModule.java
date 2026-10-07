package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Module {@code COMMON} (dữ liệu cũ của trang template "Kho Tài liệu"; kho mới dùng {@code LIBRARY}).
 * <p>
 * Fail-closed so với trước V19: đọc cần {@code MENU_FILE_EXPLORER:UPLOAD} <b>và</b> VIEW (xem) / DOWNLOAD (tải),
 * để vai trò chỉ có VIEW/DOWNLOAD kho tài liệu (ADMISSION sau V19_4) không đọc được file COMMON.
 * Xoá: chính người upload (có UPLOAD); xoá file của người khác dùng màn quản trị.
 */
@Component
public class CommonFileModule extends AbstractFileModule {

    @Override
    public String code() {
        return FileConstants.Module.COMMON;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.FILE_UPLOAD)) && any(user, List.of(Permissions.FILE_VIEW));
    }

    @Override
    public boolean canDownload(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.FILE_UPLOAD)) && any(user, List.of(Permissions.FILE_DOWNLOAD));
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return any(user, List.of(Permissions.FILE_UPLOAD));
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.FILE_UPLOAD)) && user != null && file != null
                && file.getUploadedByUserId() != null && file.getUploadedByUserId().equals(user.getId());
    }

    @Override
    public boolean referenceRequired() {
        return false;
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        // COMMON không có bảng nghiệp vụ: referenceId (nếu có) chỉ là nhãn nhóm.
    }

    @Override
    public FileReferenceInfo describeReference(Long referenceId) {
        return new FileReferenceInfo(code(), referenceId, "Tài liệu chung", null, FileConstants.RefState.NONE);
    }

    @Override
    public boolean referenceActive(Long referenceId) {
        return true;
    }
}
