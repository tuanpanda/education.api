package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "Nơi sử dụng" của file. {@code state}: ACTIVE / DELETED / NOT_FOUND / NONE. Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUsageDto {

    private String moduleName;
    private Long referenceId;
    /** Ví dụ "Học sinh HS00012 - Nguyễn Văn A", "Kho tài liệu: Biểu mẫu nhập học". */
    private String label;
    /** Route frontend để mở bản ghi; NULL nếu không có. */
    private String uiRoute;
    private String state;
}
