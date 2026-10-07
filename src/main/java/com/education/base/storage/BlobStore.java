package com.education.base.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

/**
 * Lớp lưu trữ nội dung file (blob) tách khỏi metadata {@code SYS_ATTACHED_FILES}.
 * <p>
 * Key ({@code storageKey}) là đường dẫn TƯƠNG ĐỐI do server sinh, dùng '/' làm phân cách, ví dụ
 * {@code STUDENT/2026/10/3f2b...e1.pdf} - chính là giá trị cột {@code FILE_PATH}. Không bao giờ nhận key từ client.
 * Implementation phải chặn path traversal (key chứa {@code ..}, đường dẫn tuyệt đối, ký tự điều khiển) bằng
 * {@link IllegalArgumentException}.
 * <p>
 * Hiện tại: {@code LocalDiskBlobStore} ({@code app.storage.backend=local}, QĐ-F1). Thiết kế tương thích S3/MinIO
 * (key = object key) để thêm {@code S3BlobStore} sau.
 * <p>
 * Hợp đồng Luồng 0. Chỉ Luồng A sửa interface này và viết implementation; Luồng B dùng
 * {@link #list(String)}, {@link #delete(String)}, {@link #moveToQuarantine(String)}, {@link #usage()} (mock trong test).
 */
public interface BlobStore {

    /**
     * Ghi nguyên tử: stream vào vùng tạm ({@code .tmp/}), tính SHA-256 + kích thước trong lúc ghi, rồi move vào
     * {@code key}. Không ghi đè key đã tồn tại.
     *
     * @param key          storage key đích (server sinh).
     * @param in           nội dung; implementation KHÔNG đóng stream của caller.
     * @param expectedSize kích thước mong đợi (byte) hoặc {@code -1} nếu không biết; lệch -> xoá file tạm và ném lỗi.
     * @return kích thước thực + checksum + key.
     */
    BlobInfo put(String key, InputStream in, long expectedSize) throws IOException;

    /** Mở stream đọc; caller đóng. Không tồn tại -> {@link java.nio.file.NoSuchFileException}. */
    InputStream open(String key) throws IOException;

    /** Resource cho {@code ResponseEntity<Resource>} (tải/xem). */
    Resource resource(String key) throws IOException;

    boolean exists(String key);

    /** Kích thước blob (byte); không tồn tại -> {@link java.nio.file.NoSuchFileException}. */
    long size(String key) throws IOException;

    /** Xoá vĩnh viễn; không tồn tại thì bỏ qua (idempotent). */
    void delete(String key) throws IOException;

    /**
     * Chuyển blob vào vùng cách ly ({@code .quarantine/{key}}) - dùng cho file nhiễm virus và mục đối soát.
     *
     * @return key mới trong vùng cách ly.
     */
    String moveToQuarantine(String key) throws IOException;

    /**
     * Liệt kê blob dưới {@code prefix} (rỗng = tất cả), BỎ QUA vùng {@code .tmp/} và {@code .quarantine/}.
     * Stream phải được đóng bởi caller (try-with-resources).
     */
    Stream<BlobEntry> list(String prefix) throws IOException;

    /** Dung lượng vùng lưu trữ (đĩa / bucket) để chặn upload khi sắp đầy và hiển thị dashboard. */
    StorageUsage usage() throws IOException;

    /** Mã backend lưu vào {@code STORAGE_BACKEND}: {@code LOCAL} / {@code S3}. */
    String backend();
}
