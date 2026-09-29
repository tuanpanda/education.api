package com.education.base.controller;

import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.common.ApiResponse;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.entity.FileEntity;
import com.education.base.mapper.FileMapper;
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

/**
 * Controller cho Module Quản lý File (File Management & Viewer).
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Quản lý File", description = "Upload, xem trực tiếp (inline) và tải về (attachment)")
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileMapper fileMapper;

    @Operation(summary = "Upload file",
            description = "Lưu vật lý vào {baseDir}/{MODULE}/{YYYY}/{MM}/{UUID}_{name}, Database chỉ lưu đường dẫn tương đối.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission({Permissions.FILE_UPLOAD, Permissions.STUDENT_CREATE, Permissions.STUDENT_UPDATE})
    public ApiResponse<FileResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "moduleName", required = false, defaultValue = "COMMON") String moduleName,
            @RequestParam(value = "referenceId", required = false) Long referenceId) {

        FileEntity saved = fileStorageService.storeFile(file, moduleName, referenceId);
        return ApiResponse.success("Upload file thành công.", fileMapper.toDto(saved));
    }

    @Operation(summary = "Xem file trực tiếp", description = "Content-Disposition: inline")
    @GetMapping("/view/{id}")
    @RequirePermission({Permissions.FILE_VIEW, Permissions.STUDENT_VIEW})
    public ResponseEntity<Resource> viewFile(@PathVariable("id") Long id) {
        FileEntity entity = fileStorageService.getFileEntity(id);
        Resource resource = fileStorageService.loadFileAsResource(id);

        MediaType contentType = resolveContentType(entity.getContentType());
        String contentDisposition = buildContentDisposition("inline", entity.getOriginalName());

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    @Operation(summary = "Tải file về máy", description = "Content-Disposition: attachment")
    @GetMapping("/download/{id}")
    @RequirePermission({Permissions.FILE_DOWNLOAD, Permissions.STUDENT_VIEW})
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id) {
        FileEntity entity = fileStorageService.getFileEntity(id);
        Resource resource = fileStorageService.loadFileAsResource(id);

        MediaType contentType = resolveContentType(entity.getContentType());
        String contentDisposition = buildContentDisposition("attachment", entity.getOriginalName());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);

        if (entity.getFileSize() != null) {
            builder = builder.contentLength(entity.getFileSize());
        }

        return builder.body(resource);
    }

    @Operation(summary = "Danh sách file theo nghiệp vụ",
            description = "Gọi procedure PRC_GET_FILES_BY_REF.")
    @GetMapping("/by-ref")
    @RequirePermission({Permissions.FILE_VIEW, Permissions.STUDENT_VIEW})
    public ApiResponse<List<FileResponseDto>> getByRef(
            @RequestParam("moduleName") String moduleName,
            @RequestParam("referenceId") Long referenceId) {

        List<FileEntity> files = fileStorageService.getFilesByRef(moduleName, referenceId);
        return ApiResponse.success(fileMapper.toDtoList(files));
    }

    private MediaType resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String buildContentDisposition(String disposition, String originalName) {
        String encodedName = URLEncoder.encode(originalName, StandardCharsets.UTF_8).replace("+", "%20");
        return disposition + "; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName;
    }
}
