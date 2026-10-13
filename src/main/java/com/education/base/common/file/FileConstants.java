package com.education.base.common.file;

import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Hằng số dùng chung của nền tảng file (hợp đồng Luồng 0, {@code docs/files-api.md}).
 * Giá trị khớp 1-1 với CHECK CONSTRAINT trong {@code V19_1__files_metadata_v2.sql} /
 * {@code V19_2__files_settings_maintenance.sql}.
 * <p>
 * Chỉ Luồng A sửa lớp này; luồng khác cần thêm giá trị -> ghi "Yêu cầu thay đổi hợp đồng" trong
 * {@code docs/files-api.md}.
 */
@UtilityClass
public class FileConstants {

    /** {@code SYS_ATTACHED_FILES.STATUS} ({@code CK_FILES_STATUS}). */
    public static final class Status {
        /** Đang dùng, đọc được theo luật module. */
        public static final String ACTIVE = "ACTIVE";
        /** Đã upload nhưng chưa gắn vào bản ghi (job xoá sau {@code pending-hours}). */
        public static final String PENDING = "PENDING";
        /** Trong thùng rác (xoá mềm), blob còn; xoá vĩnh viễn sau {@code PURGE_AFTER}. */
        public static final String DELETED = "DELETED";
        /** Blob đã xoá vĩnh viễn; chỉ còn dòng metadata để tra cứu. */
        public static final String PURGED = "PURGED";

        public static final String PATTERN = "ACTIVE|PENDING|DELETED|PURGED";
        public static final List<String> ALL = List.of(ACTIVE, PENDING, DELETED, PURGED);
    }

    /** {@code SYS_ATTACHED_FILES.SCAN_STATUS} ({@code CK_FILES_SCAN_STATUS}). */
    public static final class ScanStatus {
        /** Không quét ({@code app.storage.scan.mode=none}) - mặc định hiện tại (QĐ-F2). */
        public static final String SKIPPED = "SKIPPED";
        public static final String PENDING = "PENDING";
        public static final String CLEAN = "CLEAN";
        public static final String INFECTED = "INFECTED";
        public static final String ERROR = "ERROR";

        public static final String PATTERN = "SKIPPED|PENDING|CLEAN|INFECTED|ERROR";
    }

    /** {@code SYS_ATTACHED_FILES.STORAGE_BACKEND}. */
    public static final class StorageBackend {
        public static final String LOCAL = "LOCAL";
        /** Dự phòng ({@code S3BlobStore} chưa làm - QĐ-F1). */
        public static final String S3 = "S3";
    }

    /** {@code SYS_ATTACHED_FILES.UPLOADED_BY_TYPE}. */
    public static final class UploaderType {
        public static final String STAFF = "STAFF";
        public static final String STUDENT = "STUDENT";
        /** Job / tác vụ nền. */
        public static final String SYSTEM = "SYSTEM";
    }

    /**
     * Mã module file ({@code SYS_ATTACHED_FILES.MODULE_NAME} = {@code SYS_FILE_MODULE_SETTINGS.MODULE_NAME}).
     * Mỗi module ĐANG BẬT phải có đúng một bean {@link FileModuleDescriptor}; module không có descriptor bị
     * từ chối (fail-closed).
     */
    public static final class Module {
        public static final String STUDENT = "STUDENT";
        /** File Excel import học sinh (đường dẫn {@code STUDENT/IMPORT/...}, MODULE_NAME vẫn là {@code STUDENT}). */
        public static final String STUDENT_IMPORT = "STUDENT_IMPORT";
        public static final String CLASS = "CLASS";
        public static final String LEAD = "LEAD";
        public static final String TUITION = "TUITION";
        public static final String COMMON = "COMMON";
        /** Kho tài liệu dùng chung (QĐ-F6 = làm ngay): {@code REFERENCE_ID = DOC_ITEMS.ID}. Luồng D. */
        public static final String LIBRARY = "LIBRARY";
        /** GĐ2 (bài tập) - seed {@code IS_ACTIVE=0}. */
        public static final String ASSIGNMENT = "ASSIGNMENT";
        public static final String SUBMISSION = "SUBMISSION";
        public static final String FEEDBACK = "FEEDBACK";
        /** GĐ3 (học liệu, V21_4 bật): {@code REFERENCE_ID = LMS_LESSONS.ID}. Descriptor LessonFileModule (Bot 1 GĐ3). */
        public static final String LESSON = "LESSON";
    }

    /** Trạng thái bản ghi nghiệp vụ được tham chiếu ("nơi sử dụng"). */
    public static final class RefState {
        public static final String ACTIVE = "ACTIVE";
        /** Bản ghi đã xoá mềm (QĐ-F9: chỉ admin / MENU_FILE_MANAGER xem được file). */
        public static final String DELETED = "DELETED";
        public static final String NOT_FOUND = "NOT_FOUND";
        /** File không gắn bản ghi ({@code REFERENCE_ID} NULL, ví dụ file import). */
        public static final String NONE = "NONE";

        public static final String PATTERN = "ACTIVE|DELETED|NOT_FOUND|NONE";
    }

    /** {@code SYS_FILE_QUOTAS.SCOPE_TYPE}. */
    public static final class QuotaScope {
        public static final String GLOBAL = "GLOBAL";
        public static final String STUDENT_DEFAULT = "STUDENT_DEFAULT";
        public static final String STUDENT = "STUDENT";
        public static final String USER = "USER";

        public static final String PATTERN = "GLOBAL|STUDENT_DEFAULT|STUDENT|USER";
    }

    /** {@code SYS_FILE_SCAN_RUNS} / {@code SYS_FILE_SCAN_ITEMS}. */
    public static final class Scan {
        public static final String RUN_ORPHAN = "ORPHAN";
        public static final String RUN_CHECKSUM = "CHECKSUM";

        public static final String RUN_RUNNING = "RUNNING";
        public static final String RUN_DONE = "DONE";
        public static final String RUN_FAILED = "FAILED";

        public static final String ITEM_MISSING_BLOB = "MISSING_BLOB";
        public static final String ITEM_ORPHAN_BLOB = "ORPHAN_BLOB";
        public static final String ITEM_SIZE_MISMATCH = "SIZE_MISMATCH";
        public static final String ITEM_CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";
        public static final String ITEM_REF_DELETED = "REF_DELETED";
        public static final String ITEM_REF_NOT_FOUND = "REF_NOT_FOUND";

        /** Hành động xử lý một mục đối soát (request {@code action}). */
        public static final String ACTION_PATTERN = "MARK_MISSING|QUARANTINE|DELETE_BLOB|IGNORE";

        /** Giá trị lưu trong {@code SYS_FILE_SCAN_ITEMS.RESOLUTION}. */
        public static final String RESOLUTION_MARKED_MISSING = "MARKED_MISSING";
        public static final String RESOLUTION_QUARANTINED = "QUARANTINED";
        public static final String RESOLUTION_BLOB_DELETED = "BLOB_DELETED";
        public static final String RESOLUTION_IGNORED = "IGNORED";
    }

    /** Giới hạn thao tác hàng loạt và phân trang của API quản trị. */
    public static final int MAX_BULK_IDS = 100;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_REASON_LENGTH = 500;
}
