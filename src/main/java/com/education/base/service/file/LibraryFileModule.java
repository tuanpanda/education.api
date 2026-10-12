package com.education.base.service.file;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.document.DocumentConstants;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.DocItemEntity;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.DocItemRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.service.DocumentAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Descriptor module file {@code LIBRARY} (kho tài liệu, Luồng D). File chỉ được tạo / thay qua
 * {@code /api/v1/documents/items}; API file chung chỉ dùng để xem / tải khi tài liệu chứa file thấy được.
 */
@Component
@RequiredArgsConstructor
public class LibraryFileModule implements FileModuleDescriptor {

    static final String UI_ROUTE = "/documents/files?item=";

    private final DocItemRepository itemRepository;
    private final DocumentAccessService accessService;

    @Override
    public String code() {
        return DocumentConstants.FILE_MODULE;
    }

    @Override
    public boolean canRead(AuthUserPrincipal user, FileEntity file) {
        if (user == null || !user.isStaff() || file == null || file.getId() == null) {
            return false;
        }
        DocItemEntity item = itemRepository.findFirstByFileIdAndIsDeleted(file.getId(), PersistenceFlags.NOT_DELETED)
                .orElse(null);
        if (item == null || !Objects.equals(item.getId(), file.getReferenceId())) {
            return false;
        }
        return accessService.forUser(user).itemVisible(item);
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

    /**
     * Tài liệu mới: file được lưu TRƯỚC khi chèn dòng {@code DOC_ITEMS} (ID lấy trước từ sequence), nên ID chưa
     * có dòng được chấp nhận; chỉ từ chối tài liệu đã xoá.
     */
    @Override
    public void assertReferenceUsable(Long referenceId) {
        if (referenceId == null) {
            throw new OracleBusinessException(FileErrorCodes.FILE_REFERENCE_REQUIRED, "Thiếu mã tài liệu.");
        }
        itemRepository.findById(referenceId).ifPresent(item -> {
            if (!Objects.equals(item.getIsDeleted(), PersistenceFlags.NOT_DELETED)) {
                throw new OracleBusinessException(FileErrorCodes.FILE_REFERENCE_INACTIVE, "Tài liệu đã bị xoá.");
            }
        });
    }

    @Override
    public FileReferenceInfo describeReference(Long referenceId) {
        if (referenceId == null) {
            return FileReferenceInfo.none(code());
        }
        return itemRepository.findById(referenceId)
                .map(item -> new FileReferenceInfo(code(), referenceId, item.getTitle(), UI_ROUTE + referenceId,
                        Objects.equals(item.getIsDeleted(), PersistenceFlags.NOT_DELETED)
                                ? FileConstants.RefState.ACTIVE : FileConstants.RefState.DELETED))
                .orElseGet(() -> FileReferenceInfo.notFound(code(), referenceId));
    }
}