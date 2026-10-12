package com.education.base.dto.request;

import com.education.base.common.document.DocumentConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tạo / sửa / di chuyển thư mục ({@code POST /api/v1/documents/folders}, {@code PUT /api/v1/documents/folders/{id}}).
 * {@code parentId} NULL = thư mục gốc. Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocFolderUpsertRequest {

    private Long parentId;

    @NotBlank(message = "Vui lòng nhập tên thư mục")
    @Size(max = DocumentConstants.FOLDER_NAME_MAX, message = "Tên thư mục không được vượt quá 200 ký tự")
    private String name;

    @Size(max = DocumentConstants.FOLDER_DESCRIPTION_MAX, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
