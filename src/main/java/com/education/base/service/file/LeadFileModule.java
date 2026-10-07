package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.entity.LeadEntity;
import com.education.base.repository.LeadRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Module file {@code LEAD}: nguồn tuyển sinh ({@code REFERENCE_ID = EDU_LEADS.ID}).
 */
@Component
@RequiredArgsConstructor
public class LeadFileModule extends AbstractFileModule {

    private final LeadRepository repository;

    @Override
    public String code() {
        return FileConstants.Module.LEAD;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.LEAD_VIEW));
    }

    @Override
    public boolean canUpload(AuthUserPrincipal user, Long referenceId) {
        return any(user, List.of(Permissions.LEAD_CREATE, Permissions.LEAD_UPDATE));
    }

    @Override
    public boolean canDelete(AuthUserPrincipal user, FileEntity file) {
        return any(user, List.of(Permissions.LEAD_UPDATE));
    }

    @Override
    public void assertReferenceUsable(Long referenceId) {
        LeadEntity e = repository.findById(referenceId).orElseThrow(() -> referenceNotFound(code(), referenceId));
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
                .map(e -> new FileReferenceInfo(code(), referenceId, "Nguồn tuyển sinh " + e.getLeadCode() + " - " + e.getFullName(),
                        "/admission/leads?id=" + referenceId,
                        e.getIsDeleted() != null && e.getIsDeleted() != 0
                                ? FileConstants.RefState.DELETED : FileConstants.RefState.ACTIVE))
                .orElseGet(() -> FileReferenceInfo.notFound(code(), referenceId));
    }
}
