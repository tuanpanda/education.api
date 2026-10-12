package com.education.base.dto.request;

import com.education.base.common.document.DocumentConstants;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tạo mục tài liệu: {@code POST /api/v1/documents/items} multipart/form-data gồm part {@code file} + các field
 * dưới đây (binding {@code @ModelAttribute}). {@code tags}: lặp tham số {@code tags=a&tags=b} hoặc một chuỗi phân tách
 * bằng dấu phẩy. {@code title} trống -> lấy tên file (bỏ đuôi). Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocItemCreateRequest {

    /** NULL = gốc kho (chỉ người có MANAGE). */
    private Long folderId;

    @Size(max = DocumentConstants.ITEM_TITLE_MAX, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    @Size(max = DocumentConstants.ITEM_DESCRIPTION_MAX, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;

    @Size(max = DocumentConstants.MAX_TAGS_PER_ITEM, message = "Tối đa 10 thẻ")
    private List<@Size(max = DocumentConstants.TAG_MAX, message = "Mỗi thẻ tối đa 50 ký tự") String> tags;

    @Size(max = DocumentConstants.VERSION_NOTE_MAX, message = "Ghi chú phiên bản không được vượt quá 500 ký tự")
    private String versionNote;
}
