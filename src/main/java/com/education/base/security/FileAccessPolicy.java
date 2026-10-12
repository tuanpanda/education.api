package com.education.base.security;

import com.education.base.audit.AuditActions;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.config.FileStorageProperties;
import com.education.base.entity.FileEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.AuditService;
import com.education.base.service.file.FileLimitService;
import com.education.base.service.file.FileModuleRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Phân quyền file theo module + trạng thái (Luồng A, {@code docs/files-api.md} mục 4).
 * <p>
 * {@code @RequirePermission} trên {@code /api/v1/files/**} chỉ là cổng thô. Quyết định thật:
 * <ol>
 *     <li>Admin ({@link Permissions#ADMIN_ROLE}) và {@code MENU_FILE_MANAGER:DOWNLOAD} (đọc) /
 *     {@code MENU_FILE_MANAGER:DELETE} (xoá) luôn qua.</li>
 *     <li>File không {@code ACTIVE} ({@code DELETED / PENDING / PURGED}) hoặc bản ghi tham chiếu đã xoá / không còn
 *     (QĐ-F9): chỉ nhóm trên.</li>
 *     <li>Module không có {@link FileModuleDescriptor} -> từ chối (fail-closed, sửa S1).</li>
 *     <li>Luật nghiệp vụ của descriptor (kể cả phạm vi giáo viên - QĐ-F12).</li>
 * </ol>
 * File {@code INFECTED} không bao giờ phục vụ; {@code PENDING/ERROR} bị chặn khi bật quét fail-closed.
 * Mọi lần từ chối ghi {@code FILE_ACCESS_DENIED}.
 */
@Component
@RequiredArgsConstructor
public class FileAccessPolicy {

    public static final String FORBIDDEN_CODE = FileErrorCodes.FILE_FORBIDDEN;

    public enum Action { VIEW, DOWNLOAD, LIST, UPLOAD, DELETE }

    private final FileModuleRegistry registry;
    private final FileLimitService limitService;
    private final FileStorageProperties storageProperties;
    private final AuditService auditService;

    /** Kiểm tra quyền xem/tải một file đã lưu (dựa trên metadata, không tin tham số client). */
    public void checkRead(FileEntity file, Action action) {
        checkScan(file);
        AuthUserPrincipal user = SecurityUtils.requireCurrentUser();
        if (isManager(user, Permissions.FILE_MANAGER_DOWNLOAD)) {
            return;
        }
        Optional<FileModuleDescriptor> d = registry.forFile(file);
        boolean allowed = d.isPresent() && isActive(file) && referenceActive(d.get(), file.getReferenceId())
                && (action == Action.DOWNLOAD ? d.get().canDownload(user, file) : d.get().canRead(user, file));
        if (!allowed) {
            deny(file, action, file.getModuleName(), file.getReferenceId());
        }
    }

    /** @deprecated dùng {@link #checkList(String, Long)} để kiểm tra cả bản ghi tham chiếu. */
    @Deprecated
    public void checkList(String moduleName) {
        checkList(moduleName, null);
    }

    /** Kiểm tra quyền liệt kê file của một bản ghi ({@code by-ref}). */
    public void checkList(String moduleName, Long referenceId) {
        AuthUserPrincipal user = SecurityUtils.requireCurrentUser();
        if (isManager(user, Permissions.FILE_MANAGER_VIEW)) {
            return;
        }
        String module = FileModuleRegistry.normalize(moduleName);
        FileEntity probe = FileEntity.builder().moduleName(module).referenceId(referenceId).build();
        Optional<FileModuleDescriptor> d = registry.find(module);
        boolean allowed = d.isPresent() && referenceActive(d.get(), referenceId) && d.get().canRead(user, probe);
        if (!allowed) {
            deny(null, Action.LIST, module, referenceId);
        }
    }

    /**
     * Kiểm tra upload qua {@code /api/v1/files/upload} (thứ tự theo hợp đồng): module có descriptor và cho upload
     * chung ({@code INVALID_MODULE}) -> module bật -> {@code referenceId} bắt buộc / tồn tại / còn hiệu lực -> quyền ghi.
     */
    public void checkUpload(String moduleName, Long referenceId) {
        String module = FileModuleRegistry.normalize(moduleName);
        FileModuleDescriptor d = registry.find(module)
                .filter(FileModuleDescriptor::allowGenericUpload)
                .orElseThrow(() -> new OracleBusinessException(FileErrorCodes.INVALID_MODULE,
                        "Module '" + module + "' không hỗ trợ upload file."));
        limitService.requireActive(module);
        if (d.referenceRequired() && referenceId == null) {
            throw new OracleBusinessException(FileErrorCodes.FILE_REFERENCE_REQUIRED,
                    "Vui lòng chọn bản ghi (referenceId) để đính kèm file cho module " + module + ".");
        }
        if (referenceId != null) {
            d.assertReferenceUsable(referenceId);
        }
        AuthUserPrincipal user = SecurityUtils.requireCurrentUser();
        if (user.isAdmin()) {
            return;
        }
        if (!d.canUpload(user, referenceId)) {
            deny(null, Action.UPLOAD, module, referenceId);
        }
    }

    /** Kiểm tra xoá mềm qua {@code DELETE /api/v1/files/{id}}. */
    public void checkDelete(FileEntity file) {
        if (!canDelete(file)) {
            deny(file, Action.DELETE, file.getModuleName(), file.getReferenceId());
        }
    }

    /** Người dùng hiện tại có được xoá mềm file này không (không ghi nhật ký) - cho {@code FileResponseDto.canDelete}. */
    public boolean canDelete(FileEntity file) {
        AuthUserPrincipal user = SecurityUtils.currentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (isManager(user, Permissions.FILE_MANAGER_DELETE)) {
            return true;
        }
        Optional<FileModuleDescriptor> d = registry.forFile(file);
        return d.isPresent() && isActive(file) && referenceActive(d.get(), file.getReferenceId())
                && d.get().canDelete(user, file);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkScan(FileEntity file) {
        String scan = file.getScanStatus();
        if (FileConstants.ScanStatus.INFECTED.equals(scan)) {
            throw new OracleBusinessException(FileErrorCodes.FILE_INFECTED, "File có dấu hiệu chứa mã độc.");
        }
        boolean failClosed = !"none".equalsIgnoreCase(storageProperties.getScan().getMode())
                && storageProperties.getScan().getClamav().isFailClosed();
        if (failClosed && (FileConstants.ScanStatus.PENDING.equals(scan) || FileConstants.ScanStatus.ERROR.equals(scan))) {
            throw new OracleBusinessException(FileErrorCodes.FILE_SCAN_PENDING,
                    "File đang chờ quét virus, vui lòng thử lại sau.");
        }
    }

    private static boolean isManager(AuthUserPrincipal user, String managerPermission) {
        return user.isAdmin() || user.hasPermission(managerPermission);
    }

    private static boolean isActive(FileEntity f) {
        if (f.getIsDeleted() != null && f.getIsDeleted() != 0) {
            return false;
        }
        return f.getStatus() == null || FileConstants.Status.ACTIVE.equals(f.getStatus());
    }

    private static boolean referenceActive(FileModuleDescriptor d, Long referenceId) {
        return referenceId == null || d.referenceActive(referenceId);
    }

    private void deny(FileEntity file, Action action, String module, Long referenceId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("action", action.name());
        details.put("module", module);
        details.put("refId", referenceId);
        auditService.denied(AuditActions.FILE_ACCESS_DENIED, AuditActions.RESOURCE_FILE,
                file == null ? null : file.getId(), details);
        throw new ForbiddenException(FORBIDDEN_CODE, "Bạn không có quyền truy cập file của chức năng này.");
    }
}
