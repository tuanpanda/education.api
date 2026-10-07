package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Nút cây thư mục kho tài liệu ({@code GET /api/v1/documents/folders/tree}) - chỉ gồm thư mục người dùng nhìn thấy.
 * Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocFolderTreeNodeDto {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    /**
     * {@code true} nếu thư mục chỉ hiện để điều hướng (người dùng không được chia sẻ thư mục này, nhưng có mục /
     * thư mục con được chia sẻ bên trong).
     */
    private boolean navigationOnly;
    /** Số mục tài liệu (trực tiếp) người dùng nhìn thấy. */
    private long itemCount;
    @Builder.Default
    private List<DocFolderTreeNodeDto> children = List.of();
}
