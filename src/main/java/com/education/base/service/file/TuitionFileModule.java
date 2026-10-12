package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Module file {@code TUITION}: học phí ({@code REFERENCE_ID = EDU_TUITION_FEES.ID}); gắn học sinh của khoản phí để lọc (không tính quota mặc định).
 */
@Component
@RequiredArgsConstructor
public class TuitionFileModule extends AbstractFileModule {

    private final TuitionFeeRepository repository;

    @Override
    public String code() {
        return FileConstants.Module.TUITION;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.TUITION_FEE_VIEW));
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return any(user, List.of(Permissions.TUITION_FEE_CREATE, Permissions.TUITION_FEE_UPDATE));
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.TUITION_FEE_UPDATE));
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        TuitionFeeEntity e = repository.findById(referenceId).orElseThrow(() -> referenceNotFound(code(), referenceId));
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
                .map(e -> new FileReferenceInfo(code(), referenceId, "Học phí " + e.getFeeCode(),
                        "/finance/fees?id=" + referenceId,
                        e.getIsDeleted() != null && e.getIsDeleted() != 0
                                ? FileConstants.RefState.DELETED : FileConstants.RefState.ACTIVE))
                .orElseGet(() -> FileReferenceInfo.notFound(code(), referenceId));
    }

    @Override
    public Optional<Long> ownerStudentId(Long referenceId) {
        return referenceId == null ? Optional.empty()
                : repository.findById(referenceId).map(TuitionFeeEntity::getStudentId);
    }
}
