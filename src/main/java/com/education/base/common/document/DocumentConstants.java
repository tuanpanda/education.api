package com.education.base.common.document;

import lombok.experimental.UtilityClass;

/**
 * Hằng số kho tài liệu dùng chung (QĐ-F6 = làm ngay, migration {@code V19_4__document_library.sql}).
 * Giá trị khớp CHECK CONSTRAINT của {@code DOC_FOLDERS} / {@code DOC_ITEMS} / {@code DOC_SHARES}.
 * <p>
 * Chỉ Luồng D sửa lớp này.
 */
@UtilityClass
public class DocumentConstants {

    /** Module file của nội dung mục tài liệu: {@code SYS_ATTACHED_FILES.MODULE_NAME}, {@code REFERENCE_ID = DOC_ITEMS.ID}. */
    public static final String FILE_MODULE = com.education.base.common.file.FileConstants.Module.LIBRARY;

    /** {@code DOC_SHARES.TARGET_TYPE} ({@code CK_DOC_SHARES_TARGET}). */
    public static final String TARGET_ALL_STAFF = "ALL_STAFF";
    public static final String TARGET_ROLE = "ROLE";
    public static final String TARGET_CLASS = "CLASS";
    public static final String TARGET_TYPE_PATTERN = "ALL_STAFF|ROLE|CLASS";

    /** Độ sâu tối đa của cây thư mục (thư mục gốc = 1). */
    public static final int MAX_FOLDER_DEPTH = 8;

    /** Giới hạn độ dài (ký tự) - khớp {@code VARCHAR2(n CHAR)} trong V19_4. */
    public static final int FOLDER_NAME_MAX = 200;
    public static final int FOLDER_DESCRIPTION_MAX = 1000;
    public static final int ITEM_TITLE_MAX = 255;
    public static final int ITEM_DESCRIPTION_MAX = 2000;
    /** {@code DOC_ITEMS.TAGS}: chuỗi tag chuẩn hoá (chữ thường, cắt khoảng trắng) nối bằng dấu phẩy. */
    public static final int ITEM_TAGS_MAX = 500;
    public static final int TAG_MAX = 50;
    public static final int MAX_TAGS_PER_ITEM = 10;
    public static final int VERSION_NOTE_MAX = 500;
    /** Số đích chia sẻ tối đa trên một thư mục / mục. */
    public static final int MAX_SHARES_PER_TARGET = 50;

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}
