package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dashboard dung lượng ({@code GET /api/v1/admin/files/stats}). Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileStatsDto {

    /** Tổng theo STATUS. */
    @Builder.Default
    private List<FileStatBucketDto> byStatus = List.of();
    /** Theo MODULE_NAME (chỉ ACTIVE). */
    @Builder.Default
    private List<FileStatBucketDto> byModule = List.of();
    /** 12 tháng gần nhất theo CREATED_AT, key {@code yyyy-MM}, đủ 12 phần tử (tháng trống = 0). */
    @Builder.Default
    private List<FileStatBucketDto> byMonth = List.of();
    @Builder.Default
    private List<FileTopUserDto> topUploaders = List.of();
    @Builder.Default
    private List<FileTopUserDto> topStudents = List.of();
    private StorageUsageDto storage;
    /** Số file ACTIVE chưa có checksum. */
    private long filesWithoutChecksum;
    /** Số file ACTIVE có bản ghi tham chiếu đã xoá mềm / không tồn tại. */
    private long filesWithDeletedReference;
    private LocalDateTime generatedAt;
}
