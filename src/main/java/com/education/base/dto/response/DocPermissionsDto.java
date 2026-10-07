package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Quyền của người dùng hiện tại trên một thư mục / mục (để frontend ẩn/hiện nút). Server vẫn kiểm tra lại.
 * Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocPermissionsDto {

    private boolean canEdit;
    private boolean canDelete;
    private boolean canShare;
    /** Thư mục: được tạo mục con. Mục: được thay file (phiên bản mới). */
    private boolean canUpload;
}
