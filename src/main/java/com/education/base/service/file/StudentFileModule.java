package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.repository.StudentRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Module file {@code STUDENT}: hồ sơ học sinh ({@code REFERENCE_ID = EDU_STUDENTS.ID}). Tính quota học sinh; giáo viên chỉ với học sinh lớp mình dạy (QĐ-F12).
 */
@Component
@RequiredArgsConstructor
public class StudentFileModule extends AbstractFileModule {

    private final StudentRepository repository;
    private final TeacherFileScope teacherFileScope;

    @Override
    public String code() {
        return FileConstants.Module.STUDENT;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.STUDENT_VIEW)) && teacherFileScope.studentAllowed(ref(file));
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return any(user, List.of(Permissions.STUDENT_CREATE, Permissions.STUDENT_UPDATE)) && teacherFileScope.studentAllowed(referenceId);
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.STUDENT_UPDATE)) && teacherFileScope.studentAllowed(ref(file));
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        StudentEntity e = repository.findById(referenceId).orElseThrow(() -> referenceNotFound(code(), referenceId));
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
                .map(e -> new FileReferenceInfo(code(), referenceId, "Học sinh " + e.getStudentCode() + " - " + e.getFullName(),
                        "/students/list?view=" + referenceId,
                        e.getIsDeleted() != null && e.getIsDeleted() != 0
                                ? FileConstants.RefState.DELETED : FileConstants.RefState.ACTIVE))
                .orElseGet(() -> FileReferenceInfo.notFound(code(), referenceId));
    }

    @Override
    public Optional<Long> ownerStudentId(Long referenceId) {
        return Optional.ofNullable(referenceId);
    }
}
