package com.education.base.storage;

/**
 * Dung lượng vùng lưu trữ.
 *
 * @param backend     {@code LOCAL} / {@code S3}.
 * @param totalBytes  tổng dung lượng; {@code -1} nếu không xác định (S3).
 * @param usableBytes dung lượng còn ghi được; {@code -1} nếu không xác định.
 * @param usedByBlobs tổng byte của blob đang lưu nếu biết, {@code -1} nếu chưa tính (thống kê dùng SUM(FILE_SIZE)).
 */
public record StorageUsage(String backend, long totalBytes, long usableBytes, long usedByBlobs) {

    /** {@code true} khi biết dung lượng trống và nó nhỏ hơn {@code minFreeBytes}. */
    public boolean isBelow(long minFreeBytes) {
        return usableBytes >= 0 && usableBytes < minFreeBytes;
    }
}
