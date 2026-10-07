package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một đợt đối soát / tính checksum ({@code SYS_FILE_SCAN_RUNS}). Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileScanRunDto {

    private Long id;
    /** ORPHAN / CHECKSUM. */
    private String runType;
    /** RUNNING / DONE / FAILED. */
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String startedBy;
    private long dbRowsChecked;
    private long blobsChecked;
    private long missingBlobCount;
    private long orphanBlobCount;
    private long mismatchCount;
    /** Số mục chưa xử lý. */
    private long unresolvedCount;
    private String detail;
}
