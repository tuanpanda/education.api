package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.DocFolderUpsertRequest;
import com.education.base.dto.request.DocItemCreateRequest;
import com.education.base.dto.request.DocItemFilterRequest;
import com.education.base.dto.request.DocItemReplaceFileRequest;
import com.education.base.dto.request.DocItemUpdateRequest;
import com.education.base.dto.request.DocSharesUpdateRequest;
import com.education.base.dto.response.DocFolderDto;
import com.education.base.dto.response.DocFolderTreeNodeDto;
import com.education.base.dto.response.DocItemDto;
import com.education.base.dto.response.DocShareDto;
import com.education.base.dto.response.DocShareTargetsDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.DocumentFolderService;
import com.education.base.service.DocumentItemService;
import com.education.base.service.DocumentShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Kho tài liệu dùng chung - docs/files-api.md mục 6 (Luồng D). Quyền chi tiết kiểm tra trong service. */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Validated
@Tag(name = "Kho tài liệu", description = "Thư mục, tài liệu, chia sẻ của kho tài liệu dùng chung")
public class DocumentController {

    private final DocumentFolderService folderService;
    private final DocumentItemService itemService;
    private final DocumentShareService shareService;

    // ---- Thư mục ---------------------------------------------------------------------------------

    @Operation(summary = "Cây thư mục thấy được")
    @GetMapping("/folders/tree")
    @RequirePermission(Permissions.DOC_LIBRARY_VIEW)
    public ApiResponse<List<DocFolderTreeNodeDto>> tree() {
        return ApiResponse.success(folderService.getTree());
    }

    @Operation(summary = "Chi tiết thư mục")
    @GetMapping("/folders/{id}")
    @RequirePermission(Permissions.DOC_LIBRARY_VIEW)
    public ApiResponse<DocFolderDto> getFolder(@PathVariable("id") Long id) {
        return ApiResponse.success(folderService.getFolder(id));
    }

    @Operation(summary = "Tạo thư mục")
    @PostMapping("/folders")
    @RequirePermission(Permissions.DOC_LIBRARY_MANAGE)
    public ApiResponse<DocFolderDto> createFolder(@Valid @RequestBody DocFolderUpsertRequest request) {
        return ApiResponse.success("Đã tạo thư mục.", folderService.create(request));
    }

    @Operation(summary = "Sửa / di chuyển thư mục")
    @PutMapping("/folders/{id}")
    @RequirePermission(Permissions.DOC_LIBRARY_MANAGE)
    public ApiResponse<DocFolderDto> updateFolder(@PathVariable("id") Long id,
                                                  @Valid @RequestBody DocFolderUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật thư mục.", folderService.update(id, request));
    }

    @Operation(summary = "Xoá thư mục rỗng")
    @DeleteMapping("/folders/{id}")
    @RequirePermission(Permissions.DOC_LIBRARY_MANAGE)
    public ApiResponse<Void> deleteFolder(@PathVariable("id") Long id) {
        folderService.delete(id);
        return ApiResponse.success("Đã xoá thư mục.", null);
    }

    @Operation(summary = "Chia sẻ của thư mục (trực tiếp + kế thừa)")
    @GetMapping("/folders/{id}/shares")
    @RequirePermission(Permissions.DOC_LIBRARY_VIEW)
    public ApiResponse<List<DocShareDto>> getFolderShares(@PathVariable("id") Long id) {
        return ApiResponse.success(shareService.getFolderShares(id));
    }

    @Operation(summary = "Thay toàn bộ chia sẻ trực tiếp của thư mục")
    @PutMapping("/folders/{id}/shares")
    @RequirePermission({Permissions.DOC_LIBRARY_SHARE, Permissions.DOC_LIBRARY_MANAGE})
    public ApiResponse<List<DocShareDto>> putFolderShares(@PathVariable("id") Long id,
                                                          @Valid @RequestBody DocSharesUpdateRequest request) {
        return ApiResponse.success("Đã cập nhật chia sẻ.", shareService.putFolderShares(id, request));
    }

    // ---- Tài liệu --------------------------------------------------------------------------------

    @Operation(summary = "Tìm tài liệu (phân trang)")
    @GetMapping("/items/search")
    @RequirePermission(Permissions.DOC_LIBRARY_VIEW)
    public ApiResponse<PageResponse<DocItemDto>> search(@Valid @ModelAttribute DocItemFilterRequest filter) {
        return ApiResponse.success(itemService.search(filter));
    }

    @Operation(summary = "Chi tiết tài liệu")
    @GetMapping("/items/{id}")
    @RequirePermission(Permissions.DOC_LIBRARY_VIEW)
    public ApiResponse<DocItemDto> getItem(@PathVariable("id") Long id) {
        return ApiResponse.success(itemService.getItem(id));
    }

    @Operation(summary = "Tạo tài liệu (upload file)")
    @PostMapping(value = "/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permissions.DOC_LIBRARY_UPLOAD)
    public ApiResponse<DocItemDto> createItem(@RequestPart("file") MultipartFile file,
                                              @Valid @ModelAttribute DocItemCreateRequest request) {
        return ApiResponse.success("Đã tạo tài liệu.", itemService.create(file, request));
    }

    @Operation(summary = "Sửa / di chuyển tài liệu")
    @PutMapping("/items/{id}")
    @RequirePermission({Permissions.DOC_LIBRARY_UPLOAD, Permissions.DOC_LIBRARY_UPDATE,
            Permissions.DOC_LIBRARY_MANAGE})
    public ApiResponse<DocItemDto> updateItem(@PathVariable("id") Long id,
                                              @Valid @RequestBody DocItemUpdateRequest request) {
        return ApiResponse.success("Đã cập nhật tài liệu.", itemService.update(id, request));
    }

    @Operation(summary = "Thay file (tăng phiên bản)")
    @PostMapping(value = "/items/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission({Permissions.DOC_LIBRARY_UPLOAD, Permissions.DOC_LIBRARY_UPDATE,
            Permissions.DOC_LIBRARY_MANAGE})
    public ApiResponse<DocItemDto> replaceFile(@PathVariable("id") Long id,
                                               @RequestPart("file") MultipartFile file,
                                               @Valid @ModelAttribute DocItemReplaceFileRequest request) {
        return ApiResponse.success("Đã thay file.", itemService.replaceFile(id, file, request.getVersionNote()));
    }

    @Operation(summary = "Xoá tài liệu (file vào thùng rác)")
    @DeleteMapping("/items/{id}")
    @RequirePermission({Permissions.DOC_LIBRARY_UPLOAD, Permissions.DOC_LIBRARY_DELETE,
            Permissions.DOC_LIBRARY_MANAGE})
    public ApiResponse<Void> deleteItem(@PathVariable("id") Long id) {
        itemService.delete(id);
        return ApiResponse.success("Đã xoá tài liệu.", null);
    }

    @Operation(summary = "Chia sẻ của tài liệu (trực tiếp + kế thừa)")
    @GetMapping("/items/{id}/shares")
    @RequirePermission(Permissions.DOC_LIBRARY_VIEW)
    public ApiResponse<List<DocShareDto>> getItemShares(@PathVariable("id") Long id) {
        return ApiResponse.success(shareService.getItemShares(id));
    }

    @Operation(summary = "Thay toàn bộ chia sẻ trực tiếp của tài liệu")
    @PutMapping("/items/{id}/shares")
    @RequirePermission({Permissions.DOC_LIBRARY_UPLOAD, Permissions.DOC_LIBRARY_SHARE,
            Permissions.DOC_LIBRARY_MANAGE})
    public ApiResponse<List<DocShareDto>> putItemShares(@PathVariable("id") Long id,
                                                        @Valid @RequestBody DocSharesUpdateRequest request) {
        return ApiResponse.success("Đã cập nhật chia sẻ.", shareService.putItemShares(id, request));
    }

    // ---- Khác ------------------------------------------------------------------------------------

    @Operation(summary = "Đích chia sẻ được phép")
    @GetMapping("/share-targets")
    @RequirePermission({Permissions.DOC_LIBRARY_UPLOAD, Permissions.DOC_LIBRARY_SHARE,
            Permissions.DOC_LIBRARY_MANAGE})
    public ApiResponse<DocShareTargetsDto> shareTargets() {
        return ApiResponse.success(shareService.getShareTargets());
    }

    @Operation(summary = "Gợi ý thẻ")
    @GetMapping("/tags")
    @RequirePermission(Permissions.DOC_LIBRARY_VIEW)
    public ApiResponse<List<String>> tags(@RequestParam(value = "q", required = false) @Size(max = 50) String q) {
        return ApiResponse.success(itemService.suggestTags(q));
    }
}