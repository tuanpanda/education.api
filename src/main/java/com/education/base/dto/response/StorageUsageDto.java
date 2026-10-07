package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dung lượng kho lưu trữ + quota toàn hệ thống. Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageUsageDto {

    /** LOCAL / S3. */
    private String backend;
    /** -1 nếu không xác định. */
    private long totalBytes;
    private long usableBytes;
    /** {@code app.storage.min-free-disk} (byte). */
    private long minFreeBytes;
    /** {@code true} khi usableBytes &lt; minFreeBytes -> upload đang bị chặn ({@code STORAGE_LOW_DISK}). */
    private boolean lowDisk;

    /** Tổng {@code FILE_SIZE} của file ACTIVE + PENDING + DELETED (blob còn trên đĩa). */
    private long usedBytes;
    /** Quota toàn hệ thống (byte); NULL = không giới hạn. */
    private Long globalQuotaBytes;
    /** % đã dùng của quota toàn hệ thống (0-100+, 1 chữ số thập phân); NULL nếu không có quota. */
    private Double globalQuotaPercent;
    private Integer warnPercent;
}
