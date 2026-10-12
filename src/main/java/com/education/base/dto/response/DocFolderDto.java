package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thư mục kho tài liệu ({@code DOC_FOLDERS}). Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocFolderDto {

    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private Integer sortOrder;
    /** Đường dẫn từ gốc, KHÔNG gồm chính thư mục này. */
    @Builder.Default
    private List<DocBreadcrumbDto> breadcrumbs = List.of();
    private long childFolderCount;
    private long itemCount;
    private boolean navigationOnly;
    /** Chia sẻ trực tiếp trên thư mục (chỉ trả khi người dùng có quyền chia sẻ; ngược lại rỗng). */
    @Builder.Default
    private List<DocShareDto> shares = List.of();
    private DocPermissionsDto permissions;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
