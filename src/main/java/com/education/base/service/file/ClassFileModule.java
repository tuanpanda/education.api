package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.repository.ClassRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Module file {@code CLASS}: tài liệu lớp ({@code REFERENCE_ID = EDU_CLASSES.ID}); giáo viên chỉ với lớp mình dạy (QĐ-F12).
 */
@Component
@RequiredArgsConstructor
public class ClassFileModule extends AbstractFileModule {

    private final ClassRepository repository;
    private final TeacherFileScope teacherFileScope;

    @Override
    public String code() {
        return FileConstants.Module.CLASS;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.CLASS_VIEW)) && teacherFileScope.classAllowed(ref(file));
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return any(user, List.of(Permissions.CLASS_CREATE, Permissions.CLASS_UPDATE)) && teacherFileScope.classAllowed(referenceId);
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.CLASS_UPDATE)) && teacherFileScope.classAllowed(ref(file));
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        ClassEntity e = repository.findById(referenceId).orElseThrow(() -> referenceNotFound(code(), referenceId));
        if (e.getIsDeleted() != null && e.getIsDeleted() != 0) {
            throw referenceInactive(code(), referenceId);
        }
    }

    @Override
    public FileReferenceInfo describeReference(Long referenceId) {
        if (referenceId == null) {
            return FileReferenceInfo.none(code());
        }
        return repository.findById(referenceId)
                .map(e -> new FileReferenceInfo(code(), referenceId, "Lớp " + e.getClassCode() + " - " + e.getClassName(),
                        "/classes?id=" + referenceId,
                        e.getIsDeleted() != null && e.getIsDeleted() != 0
                                ? FileConstants.RefState.DELETED : FileConstants.RefState.ACTIVE))
                .orElseGet(() -> FileReferenceInfo.notFound(code(), referenceId));
    }
}
