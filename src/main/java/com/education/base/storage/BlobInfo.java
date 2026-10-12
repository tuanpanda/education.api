package com.education.base.storage;

/**
 * Kết quả {@link BlobStore#put}: blob đã ghi xong.
 *
 * @param key            storage key (= {@code SYS_ATTACHED_FILES.FILE_PATH}).
 * @param size           kích thước thực (byte).
 * @param checksumSha256 SHA-256 hex thường, 64 ký tự.
 * @param backend        {@code LOCAL} / {@code S3}.
 */
public record BlobInfo(String key, long size, String checksumSha256, String backend) {
}
