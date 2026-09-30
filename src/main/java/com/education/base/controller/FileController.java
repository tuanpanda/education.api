package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.common.file.SafeFileType;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.entity.FileEntity;
import com.education.base.mapper.FileMapper;
import com.education.base.security.FileAccessPolicy;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Controller cho Module Quản lý File (File Management & Viewer).
 * <p>
 * {@link RequirePermission} trên từng endpoint chỉ là cổng thô (có ít nhất một quyền liên quan tới file);
 * quyền chi tiết theo Module/bản ghi do {@link FileAccessPolicy} kiểm tra. Mọi response nội dung file
 * luôn kèm {@code X-Content-Type-Options: nosniff} và {@code Content-Security-Policy: sandbox};
 * chỉ ảnh/PDF được hiển thị inline, còn lại bắt buộc {@code attachment}.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Quản lý File", description = "Upload, xem trực tiếp (inline) và tải về (attachment)")
public class FileController {

    static final String NOSNIFF = "nosniff";
    static final String CSP_SANDBOX = "sandbox";

    private final FileStorageService fileStorageService;
    private final FileMapper fileMapper;
    private final FileAccessPolicy fileAccessPolicy;

    @Operation(summary = "Upload file",
            description = "Lưu vật lý vào {baseDir}/{MODULE}/{YYYY}/{MM}/{UUID}_{name}, Database chỉ lưu đường dẫn tương đối. "
                    + "Chỉ nhận ảnh (jpg/png/gif/webp), pdf, doc/docx, xls/xlsx, csv, txt, zip; kiểm tra bằng magic bytes.")
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
        return ApiResponse.success("Upload file thành công.", fileMapper.toDto(saved));
    }

    @Operation(summary = "Xem file trực tiếp",
            description = "Content-Disposition: inline cho ảnh/PDF, attachment cho các định dạng khác.")
    @GetMapping("/view/{id}")
    @RequirePermission({Permissions.FILE_VIEW, Permissions.STUDENT_VIEW, Permissions.STUDENT_IMPORT,
            Permissions.STUDENT_CREATE, Permissions.CLASS_VIEW, Permissions.LEAD_VIEW, Permissions.TUITION_FEE_VIEW})
    public ResponseEntity<Resource> viewFile(@PathVariable("id") Long id) {
        FileEntity entity = fileStorageService.getFileEntity(id);
        fileAccessPolicy.checkRead(entity, FileAccessPolicy.Action.VIEW);
        Resource resource = fileStorageService.loadFileAsResource(id);

        Optional<SafeFileType> type = SafeFileType.forStoredFile(entity.getContentType(), entity.getOriginalName());
        boolean inline = type.map(SafeFileType::isInlineSafe).orElse(false);
        return fileResponse(entity, resource, type, inline ? "inline" : "attachment");
    }

    @Operation(summary = "Tải file về máy", description = "Content-Disposition: attachment")
    @GetMapping("/download/{id}")
    @RequirePermission({Permissions.FILE_DOWNLOAD, Permissions.STUDENT_VIEW, Permissions.STUDENT_IMPORT,
            Permissions.STUDENT_CREATE, Permissions.CLASS_VIEW, Permissions.LEAD_VIEW, Permissions.TUITION_FEE_VIEW})
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id) {
        FileEntity entity = fileStorageService.getFileEntity(id);
        fileAccessPolicy.checkRead(entity, FileAccessPolicy.Action.DOWNLOAD);
        Resource resource = fileStorageService.loadFileAsResource(id);

        Optional<SafeFileType> type = SafeFileType.forStoredFile(entity.getContentType(), entity.getOriginalName());
        return fileResponse(entity, resource, type, "attachment");
    }

    @Operation(summary = "Danh sách file theo nghiệp vụ",
            description = "Gọi procedure PRC_GET_FILES_BY_REF.")
    @GetMapping("/by-ref")
    @RequirePermission({Permissions.FILE_VIEW, Permissions.STUDENT_VIEW, Permissions.CLASS_VIEW,
            Permissions.LEAD_VIEW, Permissions.TUITION_FEE_VIEW})
    public ApiResponse<List<FileResponseDto>> getByRef(
            @RequestParam("moduleName") String moduleName,
            @RequestParam("referenceId") Long referenceId) {

        fileAccessPolicy.checkList(moduleName);
        List<FileEntity> files = fileStorageService.getFilesByRef(moduleName, referenceId);
        return ApiResponse.success(fileMapper.toDtoList(files));
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
                .body(resource);
    }

    private String buildContentDisposition(String disposition, String originalName) {
        String name = originalName == null || originalName.isBlank() ? "download" : originalName;
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return disposition + "; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName;
    }
}
