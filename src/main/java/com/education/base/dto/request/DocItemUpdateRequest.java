package com.education.base.dto.request;

import com.education.base.common.document.DocumentConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sửa thông tin / di chuyển mục ({@code PUT /api/v1/documents/items/{id}}, JSON). Không đổi file (xem
 * {@code POST /items/{id}/file}). Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocItemUpdateRequest {

    /** NULL = gốc kho (chỉ người có MANAGE). */
    private Long folderId;

    @NotBlank(message = "Vui lòng nhập tiêu đề")
    @Size(max = DocumentConstants.ITEM_TITLE_MAX, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    @Size(max = DocumentConstants.ITEM_DESCRIPTION_MAX, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;

    @Size(max = DocumentConstants.MAX_TAGS_PER_ITEM, message = "Tối đa 10 thẻ")
    private List<@Size(max = DocumentConstants.TAG_MAX, message = "Mỗi thẻ tối đa 50 ký tự") String> tags;
}
