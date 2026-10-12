package com.education.base.service.file;

import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.security.AuthUserPrincipal;

import java.util.List;

/** Khung chung cho descriptor theo quyền menu (Luồng A). */
abstract class AbstractFileModule implements FileModuleDescriptor {

    static boolean any(AuthUserPrincipal user, List<String> permissions) {
        if (user == null) {
            return false;
        }
        for (String p : permissions) {
            if (user.hasPermission(p)) {
                return true;
            }
        }
        return false;
    }

    static OracleBusinessException referenceNotFound(String module, Long id) {
        return new OracleBusinessException(FileErrorCodes.FILE_REFERENCE_NOT_FOUND,
                "Không tìm thấy bản ghi " + module + " với ID: " + id);
    }

    static OracleBusinessException referenceInactive(String module, Long id) {
        return new OracleBusinessException(FileErrorCodes.FILE_REFERENCE_INACTIVE,
                "Bản ghi " + module + " ID " + id + " đã bị xoá hoặc ngừng hoạt động.");
    }

    static Long ref(FileEntity file) {
        return file == null ? null : file.getReferenceId();
    }
}
