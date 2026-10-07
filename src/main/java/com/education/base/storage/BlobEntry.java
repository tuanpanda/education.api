package com.education.base.storage;

import java.time.Instant;

/**
 * Một blob trong {@link BlobStore#list(String)} (dùng cho đối soát DB - kho lưu trữ).
 *
 * @param key          storage key tương đối ('/' phân cách).
 * @param size         kích thước (byte).
 * @param lastModified thời điểm sửa cuối - đối soát bỏ qua blob mới hơn {@code orphan-grace-hours}.
 */
public record BlobEntry(String key, long size, Instant lastModified) {
}
