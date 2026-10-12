package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.common.file.FileConstants;
import com.education.base.dto.request.FileBulkRequest;
import com.education.base.dto.request.FileDeleteRequest;
import com.education.base.dto.request.FileFilterRequest;
import com.education.base.dto.request.FileModuleSettingsUpdateRequest;
import com.education.base.dto.request.FilePurgeRequest;
import com.education.base.dto.request.FileQuotasUpdateRequest;
import com.education.base.dto.request.FileScanResolveRequest;
import com.education.base.dto.response.FileAdminDto;
import com.education.base.dto.response.FileBulkResultDto;
import com.education.base.dto.response.FileDetailDto;
import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.dto.response.FileQuotaDto;
import com.education.base.dto.response.FileScanItemDto;
import com.education.base.dto.response.FileScanRunDto;
import com.education.base.dto.response.FileStatsDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StorageUsageDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.FileAdminService;
import com.education.base.service.file.FileMaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * API màn "Quản lý file" {@code /system/files} - toàn bộ dưới {@code MENU_FILE_MANAGER} (mặc định chỉ ROLE_ADMIN).
 * Hợp đồng: {@code docs/files-api.md} mục 5. Luồng B.
 */
@RestController
@RequestMapping("/api/v1/admin/files")
@RequiredArgsConstructor
@Validated
@Tag(name = "Quản lý file (quản trị)", description = "Tìm kiếm, thùng rác, thống kê, cấu hình, đối soát file")
public class FileAdminController {

    static final String NOSNIFF = "nosniff";
    static final String CSP_SANDBOX = "sandbox";

    private final FileAdminService fileAdminService;
    private final FileMaintenanceService maintenanceService;

    @Operation(summary = "Tìm kiếm file (phân trang)")
    @GetMapping("/search")
    @RequirePermission(Permissions.FILE_MANAGER_VIEW)
    public ApiResponse<PageResponse<FileAdminDto>> search(@Valid @ModelAttribute FileFilterRequest filter) {
        return ApiResponse.success(fileAdminService.search(filter));
    }

    @Operation(summary = "Chi tiết file + nơi sử dụng + 20 sự kiện nhật ký")
    @GetMapping("/{id:\\d+}")
    @RequirePermission(Permissions.FILE_MANAGER_VIEW)
    public ApiResponse<FileDetailDto> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(fileAdminService.detail(id));
    }

    @Operation(summary = "Xem file (inline với ảnh/PDF, kể cả trong thùng rác)")
    @GetMapping("/{id:\\d+}/view")
    @RequirePermission(Permissions.FILE_MANAGER_DOWNLOAD)
    public ResponseEntity<Resource> view(@PathVariable("id") Long id) {
        return fileResponse(fileAdminService.open(id, false));
    }

    @Operation(summary = "Tải file (attachment)")
    @GetMapping("/{id:\\d+}/download")
    @RequirePermission(Permissions.FILE_MANAGER_DOWNLOAD)
    public ResponseEntity<Resource> download(@PathVariable("id") Long id) {
        return fileResponse(fileAdminService.open(id, true));
    }

    @Operation(summary = "Xoá mềm (vào thùng rác)")
    @PostMapping("/{id:\\d+}/delete")
    @RequirePermission(Permissions.FILE_MANAGER_DELETE)
    public ApiResponse<FileAdminDto> delete(@PathVariable("id") Long id, @Valid @RequestBody FileDeleteRequest request) {
        return ApiResponse.success(fileAdminService.delete(id, request.getReason()));
    }

    @Operation(summary = "Xoá mềm hàng loạt (tối đa 100)")
    @PostMapping("/bulk-delete")
    @RequirePermission(Permissions.FILE_MANAGER_DELETE)
    public ApiResponse<FileBulkResultDto> bulkDelete(@Valid @RequestBody FileBulkRequest request) {
        return ApiResponse.success(fileAdminService.bulkDelete(request));
    }

    @Operation(summary = "Khôi phục từ thùng rác")
    @PostMapping("/{id:\\d+}/restore")
    @RequirePermission(Permissions.FILE_MANAGER_RESTORE)
    public ApiResponse<FileAdminDto> restore(@PathVariable("id") Long id) {
        return ApiResponse.success(fileAdminService.restore(id));
    }

    @Operation(summary = "Khôi phục hàng loạt")
    @PostMapping("/bulk-restore")
    @RequirePermission(Permissions.FILE_MANAGER_RESTORE)
    public ApiResponse<FileBulkResultDto> bulkRestore(@Valid @RequestBody FileBulkRequest request) {
        return ApiResponse.success(fileAdminService.bulkRestore(request));
    }

    @Operation(summary = "Xoá vĩnh viễn (chỉ file trong thùng rác, nhập lại tên file)")
    @PostMapping("/{id:\\d+}/purge")
    @RequirePermission(Permissions.FILE_MANAGER_PURGE)
    public ApiResponse<FileAdminDto> purge(@PathVariable("id") Long id, @Valid @RequestBody FilePurgeRequest request) {
        return ApiResponse.success(fileAdminService.purge(id, request));
    }

    @Operation(summary = "Thống kê dung lượng")
    @GetMapping("/stats")
    @RequirePermission(Permissions.FILE_MANAGER_VIEW)
    public ApiResponse<FileStatsDto> stats() {
        return ApiResponse.success(fileAdminService.stats());
    }

    @Operation(summary = "Dung lượng đĩa / quota toàn hệ thống")
    @GetMapping("/storage")
    @RequirePermission(Permissions.FILE_MANAGER_VIEW)
    public ApiResponse<StorageUsageDto> storage() {
        return ApiResponse.success(fileAdminService.storage());
    }

    @GetMapping("/settings/modules")
    @RequirePermission(Permissions.FILE_MANAGER_VIEW)
    public ApiResponse<List<FileModuleSettingDto>> moduleSettings() {
        return ApiResponse.success(fileAdminService.moduleSettings());
    }

    @PutMapping("/settings/modules")
    @RequirePermission(Permissions.FILE_MANAGER_CONFIG)
    public ApiResponse<List<FileModuleSettingDto>> updateModuleSettings(
            @Valid @RequestBody FileModuleSettingsUpdateRequest request) {
        return ApiResponse.success(fileAdminService.updateModuleSettings(request));
    }

    @GetMapping("/settings/quotas")
    @RequirePermission(Permissions.FILE_MANAGER_VIEW)
    public ApiResponse<List<FileQuotaDto>> quotas() {
        return ApiResponse.success(fileAdminService.quotas());
    }

    @PutMapping("/settings/quotas")
    @RequirePermission(Permissions.FILE_MANAGER_CONFIG)
    public ApiResponse<List<FileQuotaDto>> updateQuotas(@Valid @RequestBody FileQuotasUpdateRequest request) {
        return ApiResponse.success(fileAdminService.updateQuotas(request));
    }

    // ---- Bảo trì -----------------------------------------------------------------------------------

    @Operation(summary = "Bắt đầu đối soát DB <-> kho file (chạy nền, chỉ báo cáo)")
    @PostMapping("/maintenance/orphan-scan")
    @RequirePermission(Permissions.FILE_MANAGER_MAINTAIN)
    public ApiResponse<FileScanRunDto> orphanScan() {
        return ApiResponse.success(maintenanceService.startOrphanScan());
    }

    @Operation(summary = "Bắt đầu tính checksum cho file cũ (chạy nền)")
    @PostMapping("/maintenance/checksum-backfill")
    @RequirePermission(Permissions.FILE_MANAGER_MAINTAIN)
    public ApiResponse<FileScanRunDto> checksumBackfill() {
        return ApiResponse.success(maintenanceService.startChecksumBackfill());
    }

    @GetMapping("/maintenance/scans")
    @RequirePermission(Permissions.FILE_MANAGER_MAINTAIN)
    public ApiResponse<PageResponse<FileScanRunDto>> scans(
            @RequestParam(name = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(FileConstants.MAX_PAGE_SIZE) int pageSize) {
        return ApiResponse.success(maintenanceService.listRuns(pageNo, pageSize));
    }

    @GetMapping("/maintenance/scans/{runId:\\d+}")
    @RequirePermission(Permissions.FILE_MANAGER_MAINTAIN)
    public ApiResponse<FileScanRunDto> scan(@PathVariable("runId") Long runId) {
        return ApiResponse.success(maintenanceService.getRun(runId));
    }

    @GetMapping("/maintenance/scans/{runId:\\d+}/items")
    @RequirePermission(Permissions.FILE_MANAGER_MAINTAIN)
    public ApiResponse<PageResponse<FileScanItemDto>> scanItems(
            @PathVariable("runId") Long runId,
            @RequestParam(name = "itemType", required = false)
            @Pattern(regexp = "MISSING_BLOB|ORPHAN_BLOB|SIZE_MISMATCH|CHECKSUM_MISMATCH|REF_DELETED|REF_NOT_FOUND",
                    message = "Loại mục không hợp lệ") String itemType,
            @RequestParam(name = "resolved", required = false) Boolean resolved,
            @RequestParam(name = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(FileConstants.MAX_PAGE_SIZE) int pageSize) {
        return ApiResponse.success(maintenanceService.listItems(runId, itemType, resolved, pageNo, pageSize));
    }

    @PostMapping("/maintenance/scans/{runId:\\d+}/items/{itemId:\\d+}/resolve")
    @RequirePermission(Permissions.FILE_MANAGER_MAINTAIN)
    public ApiResponse<FileScanItemDto> resolve(@PathVariable("runId") Long runId,
                                                @PathVariable("itemId") Long itemId,
                                                @Valid @RequestBody FileScanResolveRequest request) {
        return ApiResponse.success(maintenanceService.resolveItem(runId, itemId, request));
    }

    // ---- Trả nhị phân ------------------------------------------------------------------------------

    private ResponseEntity<Resource> fileResponse(FileAdminService.FileContent content) {
        String disposition = content.inline() ? "inline" : "attachment";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition(disposition, content.file().getOriginalName()))
                .header("X-Content-Type-Options", NOSNIFF)
                .header("Content-Security-Policy", CSP_SANDBOX)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(content.resource());
    }

    static String contentDisposition(String disposition, String originalName) {
        String name = originalName == null || originalName.isBlank() ? "download" : originalName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return disposition + "; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }
}
