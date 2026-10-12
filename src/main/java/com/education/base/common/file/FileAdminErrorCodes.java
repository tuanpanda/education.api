package com.education.base.common.file;

import lombok.experimental.UtilityClass;

/**
 * Mã lỗi của API quản trị file {@code /api/v1/admin/files/**} (màn hình /system/files).
 * HTTP status theo {@code GlobalExceptionHandler}: 400, mã kết thúc {@code _NOT_FOUND} -> 404.
 * Bảng đầy đủ: {@code docs/files-api.md} mục "Mã lỗi".
 * <p>
 * Chỉ Luồng B sửa file này.
 */
@UtilityClass
public class FileAdminErrorCodes {

    public static final String FILE_INVALID_STATE = "FILE_INVALID_STATE";
    public static final String FILE_PURGE_CONFIRM_MISMATCH = "FILE_PURGE_CONFIRM_MISMATCH";
    public static final String FILE_BULK_TOO_MANY = "FILE_BULK_TOO_MANY";
    public static final String FILE_SCAN_RUNNING = "FILE_SCAN_RUNNING";
    public static final String FILE_SCAN_RUN_NOT_FOUND = "FILE_SCAN_RUN_NOT_FOUND";
    public static final String FILE_SCAN_ITEM_NOT_FOUND = "FILE_SCAN_ITEM_NOT_FOUND";
    public static final String FILE_SCAN_ITEM_RESOLVED = "FILE_SCAN_ITEM_RESOLVED";
    public static final String FILE_SETTING_INVALID = "FILE_SETTING_INVALID";
    public static final String FILE_QUOTA_INVALID = "FILE_QUOTA_INVALID";
}
