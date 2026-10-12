package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một nhóm thống kê (theo trạng thái / module / tháng). Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileStatBucketDto {

    /** Khoá nhóm: mã trạng thái, mã module hoặc tháng {@code yyyy-MM}. */
    private String key;
    /** Nhãn hiển thị (tiếng Việt) nếu có. */
    private String label;
    private long fileCount;
    private long totalBytes;
}
