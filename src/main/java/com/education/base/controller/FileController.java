package com.education.base.controller;

import com.education.base.audit.AuditActions;
import com.education.base.common.ApiResponse;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.SafeFileType;
import com.education.base.config.FilesProperties;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.FileUploadPolicyDto;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.security.AuthenticatedOnly;
import com.education.base.security.FileAccessPolicy;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.file.FileLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller cho Module Quản lý File (File Management & Viewer).
 * <p>
 * {@link RequirePermission} trên từng endpoint chỉ là cổng thô; quyền chi tiết theo module / bản ghi / trạng thái do
 * {@link FileAccessPolicy} kiểm tra. Mọi response nội dung file luôn kèm {@code X-Content-Type-Options: nosniff},
 * {@code Content-Security-Policy: sandbox}, {@code Cache-Control: private, no-store},
 * {@code Cross-Origin-Resource-Policy: same-origin}; chỉ ảnh/PDF được hiển thị inline.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Quản lý File", description = "Upload, xem trực tiếp (inline), tải về (attachment), xoá mềm")
public class FileController {

    static final String NOSNIFF = "nosniff";
    static final String CSP_SANDBOX = "sandbox";
    static final String CACHE_NO_STORE = "private, no-store";
    static final String CORP_SAME_ORIGIN = "same-origin";

    private final FileStorageService fileStorageService;
    private final FileMapper fileMapper;
    private final FileAccessPolicy fileAccessPolicy;
    private final FileLifecycleService fileLifecycleService;
    private final FileLimitService fileLimitService;
    private final AuditService auditService;
    private final FilesProperties filesProperties;

    @Operation(summary = "Upload file",
            description = "Lưu qua BlobStore vào {MODULE}/{YYYY}/{MM}/{UUID}.{ext}, tên gốc Unicode giữ trong DB. "
                    + "Kiểm tra module, bản ghi tham chiếu, quyền, giới hạn / quota và magic bytes.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission({Permissions.FILE_UPLOAD,
            Permissions.STUDENT_CREATE, Permissions.STUDENT_UPDATE,
            Permissions.CLASS_CREATE, Permissions.CLASS_UPDATE,
            Permissions.LEAD_CREATE, Permissions.LEAD_UPDATE,
            Permissions.TUITION_FEE_CREATE, Permissions.TUITION_FEE_UPDATE})
    public ApiResponse<FileResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "moduleName", required = false, defaultValue = "COMMON") String moduleName,
            @RequestParam(value = "referenceId", required = false) Long referenceId) {

        fileAccessPolicy.checkUpload(moduleName, referenceId);
        FileEntity saved = fileStorageService.storeFile(file, moduleName, referenceId);
        return ApiResponse.success("Upload file thành công.", toDto(saved));
    }

    @Operation(summary = "Xem file trực tiếp",
            description = "Content-Disposition: inline cho ảnh/PDF, attachment cho các định dạng khác.")
    @GetMapping("/view/{id}")
    @RequirePermission({Permissions.FILE_VIEW, Permissions.STUDENT_VIEW, Permissions.STUDENT_IMPORT,
            Permissions.STUDENT_CREATE, Permissions.CLASS_VIEW, Permissions.LEAD_VIEW, Permissions.TUITION_FEE_VIEW,
            Permissions.FILE_MANAGER_DOWNLOAD})
    public ResponseEntity<Resource> viewFile(@PathVariable("id") Long id) {
        FileEntity entity = fileStorageService.getFileEntity(id);
        fileAccessPolicy.checkRead(entity, FileAccessPolicy.Action.VIEW);
        Resource resource = fileStorageService.loadFileAsResource(id);
        if (filesProperties.getAudit().isViews()) {
            auditService.success(AuditActions.FILE_VIEWED, AuditActions.RESOURCE_FILE, id, details(entity));
        }

        Optional<SafeFileType> type = SafeFileType.forStoredFile(entity.getContentType(), entity.getOriginalName());
        boolean inline = type.map(SafeFileType::isInlineSafe).orElse(false);
        return fileResponse(entity, resource, type, inline ? "inline" : "attachment");
    }

    @Operation(summary = "Tải file về máy", description = "Content-Disposition: attachment; ghi nhật ký FILE_DOWNLOADED.")
    @GetMapping("/download/{id}")
    @RequirePermission({Permissions.FILE_DOWNLOAD, Permissions.STUDENT_VIEW, Permissions.STUDENT_IMPORT,
            Permissions.STUDENT_CREATE, Permissions.CLASS_VIEW, Permissions.LEAD_VIEW, Permissions.TUITION_FEE_VIEW,
            Permissions.FILE_MANAGER_DOWNLOAD})
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id) {
        FileEntity entity = fileStorageService.getFileEntity(id);
        fileAccessPolicy.checkRead(entity, FileAccessPolicy.Action.DOWNLOAD);
        Resource resource = fileStorageService.loadFileAsResource(id);
        auditService.success(AuditActions.FILE_DOWNLOADED, AuditActions.RESOURCE_FILE, id, details(entity));

        Optional<SafeFileType> type = SafeFileType.forStoredFile(entity.getContentType(), entity.getOriginalName());
        return fileResponse(entity, resource, type, "attachment");
    }

    @Operation(summary = "Danh sách file theo nghiệp vụ",
            description = "Gọi procedure PRC_GET_FILES_BY_REF (chỉ file ACTIVE).")
    @GetMapping("/by-ref")
    @RequirePermission({Permissions.FILE_VIEW, Permissions.STUDENT_VIEW, Permissions.CLASS_VIEW,
            Permissions.LEAD_VIEW, Permissions.TUITION_FEE_VIEW, Permissions.FILE_MANAGER_VIEW})
    public ApiResponse<List<FileResponseDto>> getByRef(
            @RequestParam("moduleName") String moduleName,
            @RequestParam("referenceId") Long referenceId) {

        fileAccessPolicy.checkList(moduleName, referenceId);
        List<FileEntity> files = fileStorageService.getFilesByRef(moduleName, referenceId);
        return ApiResponse.success(files.stream().map(this::toDto).toList());
    }

    @Operation(summary = "Xoá mềm file (vào thùng rác)",
            description = "Theo luật xoá của module (ví dụ STUDENT cần STUDENT_UPDATE). reason tối đa 500 ký tự.")
    @DeleteMapping("/{id}")
    @RequirePermission({Permissions.FILE_UPLOAD,
            Permissions.STUDENT_CREATE, Permissions.STUDENT_UPDATE, Permissions.STUDENT_IMPORT,
            Permissions.CLASS_CREATE, Permissions.CLASS_UPDATE,
            Permissions.LEAD_CREATE, Permissions.LEAD_UPDATE,
            Permissions.TUITION_FEE_CREATE, Permissions.TUITION_FEE_UPDATE,
            Permissions.FILE_MANAGER_DELETE})
    public ApiResponse<FileResponseDto> deleteFile(@PathVariable("id") Long id,
                                                   @RequestParam(value = "reason", required = false) String reason) {
        if (reason != null && reason.length() > FileConstants.MAX_REASON_LENGTH) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Lý do xoá tối đa 500 ký tự.");
        }
        FileEntity entity = fileStorageService.getFileEntity(id);
        fileAccessPolicy.checkDelete(entity);
        FileEntity deleted = fileLifecycleService.softDelete(id, reason);
        Map<String, Object> d = details(entity);
        if (reason != null && !reason.isBlank()) {
            d.put("reason", reason.strip());
        }
        auditService.success(AuditActions.FILE_DELETED, AuditActions.RESOURCE_FILE, id, d);
        FileResponseDto dto = fileMapper.toDto(deleted);
        dto.setCanDelete(false);
        return ApiResponse.success("Đã chuyển file vào thùng rác.", dto);
    }

    @Operation(summary = "Giới hạn upload theo module",
            description = "module bỏ trống = mọi module đang bật. Frontend kiểm tra trước, server vẫn kiểm tra lại.")
    @GetMapping("/policies")
    @AuthenticatedOnly
    public ApiResponse<List<FileUploadPolicyDto>> policies(
            @RequestParam(value = "module", required = false) String module) {
        return ApiResponse.success(fileLimitService.policies(module));
    }

    // ---------------------------------------------------------------------------------------------

    private FileResponseDto toDto(FileEntity entity) {
        FileResponseDto dto = fileMapper.toDto(entity);
        dto.setCanDelete(fileAccessPolicy.canDelete(entity));
        return dto;
    }

    private static Map<String, Object> details(FileEntity entity) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", entity.getModuleName());
        d.put("refId", entity.getReferenceId());
        d.put("size", entity.getFileSize());
        d.put("ext", entity.getFileExt() != null ? entity.getFileExt()
                : SafeFileType.extensionOfName(entity.getOriginalName()));
        return d;
    }

    private ResponseEntity<Resource> fileResponse(FileEntity entity, Resource resource,
                                                  Optional<SafeFileType> type, String disposition) {
        MediaType contentType = type
                .map(t -> MediaType.parseMediaType(t.responseContentType()))
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        // Content-Length lấy từ file vật lý (ResourceHttpMessageConverter), không tin FILE_SIZE trong DB.
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(disposition, entity.getOriginalName()))
                .header("X-Content-Type-Options", NOSNIFF)
                .header("Content-Security-Policy", CSP_SANDBOX)
                .header(HttpHeaders.CACHE_CONTROL, CACHE_NO_STORE)
                .header("Cross-Origin-Resource-Policy", CORP_SAME_ORIGIN)
                .body(resource);
    }

    private String buildContentDisposition(String disposition, String originalName) {
        String name = originalName == null || originalName.isBlank() ? "download" : originalName;
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return disposition + "; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName;
    }
}
