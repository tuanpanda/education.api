package com.education.base.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Quét virus nội dung file sau khi ghi blob (QĐ-F2: interface + {@code SCAN_STATUS} ngay; ClamAV ở GĐ2b).
 * <p>
 * Implementation: {@code NoopVirusScanner} ({@code app.storage.scan.mode=none}, luôn {@link ScanResult#skipped()}),
 * {@code ClamAvVirusScanner} ({@code mode=clamav}, chưa làm). Khi {@code fail-closed=true}, kết quả
 * {@code ERROR}/{@code PENDING} không cho tải.
 * <p>
 * Hợp đồng Luồng 0. Chỉ Luồng A sửa.
 */
public interface VirusScanner {

    /**
     * @param in          nội dung (implementation không đóng stream của caller).
     * @param displayName tên hiển thị để ghi log (không phải đường dẫn).
     */
    ScanResult scan(InputStream in, String displayName) throws IOException;

    /** {@code none} / {@code clamav}. */
    String mode();

    /**
     * Kết quả quét.
     *
     * @param status {@code SKIPPED} / {@code CLEAN} / {@code INFECTED} / {@code ERROR} / {@code PENDING}
     *               ({@link com.education.base.common.file.FileConstants.ScanStatus}).
     * @param detail tên virus / lỗi, tối đa 255 ký tự ({@code SCAN_DETAIL}); NULL nếu không có.
     */
    record ScanResult(String status, String detail) {

        public static ScanResult skipped() {
            return new ScanResult(com.education.base.common.file.FileConstants.ScanStatus.SKIPPED, null);
        }
    }
}
