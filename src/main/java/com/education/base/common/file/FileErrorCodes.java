package com.education.base.common.file;

import lombok.experimental.UtilityClass;

/**
 * Mã lỗi ({@code ApiResponse.code}) của nền tảng file (upload / xem / tải / xoá qua {@code /api/v1/files}).
 * HTTP status theo {@code GlobalExceptionHandler} hiện có: {@code OracleBusinessException} -> 400
 * (mã kết thúc {@code _NOT_FOUND} -> 404), {@code ForbiddenException} -> 403, vượt multipart -> 413.
 * Bảng đầy đủ + thông báo tiếng Việt: {@code docs/files-api.md} mục "Mã lỗi".
 * Quản trị file: {@link FileAdminErrorCodes} (Luồng B). Kho tài liệu: {@code DocErrorCodes} (Luồng D).
 * <p>
 * Chỉ Luồng A sửa file này.
 */
@UtilityClass
public class FileErrorCodes {

    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    /** Đã có từ trước ({@code FileAccessPolicy.FORBIDDEN_CODE}). */
    public static final String FILE_FORBIDDEN = "FILE_FORBIDDEN";
    public static final String INVALID_MODULE = "INVALID_MODULE";
    public static final String FILE_MODULE_DISABLED = "FILE_MODULE_DISABLED";
    public static final String FILE_REFERENCE_REQUIRED = "FILE_REFERENCE_REQUIRED";
    public static final String FILE_REFERENCE_NOT_FOUND = "FILE_REFERENCE_NOT_FOUND";
    public static final String FILE_REFERENCE_INACTIVE = "FILE_REFERENCE_INACTIVE";
    public static final String FILE_EMPTY = "FILE_EMPTY";
    public static final String FILE_TYPE_NOT_ALLOWED = "FILE_TYPE_NOT_ALLOWED";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String FILE_LIMIT_COUNT = "FILE_LIMIT_COUNT";
    public static final String FILE_LIMIT_TOTAL = "FILE_LIMIT_TOTAL";
    public static final String FILE_QUOTA_EXCEEDED = "FILE_QUOTA_EXCEEDED";
    public static final String STORAGE_LOW_DISK = "STORAGE_LOW_DISK";
    public static final String FILE_INFECTED = "FILE_INFECTED";
    /** Đang chờ quét / quét lỗi khi bật ClamAV fail-closed: chưa cho tải. */
    public static final String FILE_SCAN_PENDING = "FILE_SCAN_PENDING";
    public static final String FILE_BLOB_MISSING = "FILE_BLOB_MISSING";
    public static final String FILE_STORAGE_ERROR = "FILE_STORAGE_ERROR";
    // Đã có trước V19 (SafeFileType / FileStorageServiceImpl) - giữ nguyên để frontend không đổi.
    public static final String FILE_CONTENT_MISMATCH = "FILE_CONTENT_MISMATCH";
    public static final String FILE_TYPE_INVALID = "FILE_TYPE_INVALID";
    public static final String FILE_STORE_ERROR = "FILE_STORE_ERROR";
    public static final String FILE_READ_ERROR = "FILE_READ_ERROR";
    public static final String FILE_PATH_ERROR = "FILE_PATH_ERROR";
    public static final String INVALID_PATH = "INVALID_PATH";
}
