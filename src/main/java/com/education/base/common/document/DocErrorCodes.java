package com.education.base.common.document;

import lombok.experimental.UtilityClass;

/**
 * Mã lỗi của kho tài liệu {@code /api/v1/documents/**} (màn hình /documents/files, menu MENU_FILE_EXPLORER).
 * HTTP status theo {@code GlobalExceptionHandler}: 400, mã kết thúc {@code _NOT_FOUND} -> 404,
 * {@code DOC_FORBIDDEN} / {@code DOC_SHARE_CLASS_FORBIDDEN} ném bằng {@code ForbiddenException} -> 403.
 * Bảng đầy đủ: {@code docs/files-api.md} mục "Mã lỗi".
 * <p>
 * Chỉ Luồng D sửa file này.
 */
@UtilityClass
public class DocErrorCodes {

    public static final String DOC_FOLDER_NOT_FOUND = "DOC_FOLDER_NOT_FOUND";
    public static final String DOC_FOLDER_NAME_DUPLICATE = "DOC_FOLDER_NAME_DUPLICATE";
    public static final String DOC_FOLDER_NOT_EMPTY = "DOC_FOLDER_NOT_EMPTY";
    public static final String DOC_FOLDER_CYCLE = "DOC_FOLDER_CYCLE";
    public static final String DOC_FOLDER_TOO_DEEP = "DOC_FOLDER_TOO_DEEP";
    public static final String DOC_ITEM_NOT_FOUND = "DOC_ITEM_NOT_FOUND";
    public static final String DOC_FORBIDDEN = "DOC_FORBIDDEN";
    public static final String DOC_SHARE_INVALID = "DOC_SHARE_INVALID";
    public static final String DOC_SHARE_TARGET_NOT_FOUND = "DOC_SHARE_TARGET_NOT_FOUND";
    /** Giáo viên chia sẻ cho lớp mình không dạy (cùng tinh thần {@code NOT_CLASS_TEACHER}). */
    public static final String DOC_SHARE_CLASS_FORBIDDEN = "DOC_SHARE_CLASS_FORBIDDEN";
}
