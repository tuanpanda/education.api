package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * File Excel import học sinh ({@code MODULE_NAME=STUDENT}, đường dẫn {@code STUDENT/IMPORT/...}, không gắn bản ghi).
 * Chỉ lưu qua API import ({@code allowGenericUpload=false}).
 */
@Component
public class StudentImportFileModule extends AbstractFileModule {

    private static final List<String> READ = List.of(Permissions.STUDENT_IMPORT, Permissions.STUDENT_CREATE);

    @Override
    public String code() {
        return FileConstants.Module.STUDENT_IMPORT;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return any(user, READ);
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return any(user, List.of(Permissions.STUDENT_IMPORT));
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.STUDENT_IMPORT));
    }

    @Override
    public boolean referenceRequired() {
        return false;
    }

    @Override
    public boolean allowGenericUpload() {
        return false;
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        // Không gắn bản ghi.
    }

    @Override
    public FileReferenceInfo describeReference(Long referenceId) {
        return new FileReferenceInfo(code(), null, "Import học sinh", "/students/list", FileConstants.RefState.NONE);
    }

    @Override
    public boolean referenceActive(Long referenceId) {
        return true;
    }
}
