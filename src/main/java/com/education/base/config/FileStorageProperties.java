package com.education.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Cấu hình lưu trữ file vật lý của hệ thống, ánh xạ từ tiền tố {@code app.storage} trong
 * {@code application.yml}.
 * <p>
 * File vật lý (dữ liệu cũ) được lưu tại {@code {baseDir}/{MODULE}/{YYYY}/{MM}/{UUID}_{tenfile}}; file mới từ V19
 * (QĐ-F10) là {@code {MODULE}[/{SUB}]/{YYYY}/{MM}/{UUID}.{ext}}. Database chỉ lưu đường dẫn tương đối so với
 * {@code baseDir}.
 * <p>
 * Các nhóm {@code defaults/quota/retention/scan/jobs} là khung hợp đồng Luồng 0 (giá trị fallback khi bảng
 * {@code SYS_FILE_MODULE_SETTINGS} / {@code SYS_FILE_QUOTAS} chưa có dòng - QĐ-F8). Chỉ Luồng A sửa lớp này;
 * Luồng B đọc {@code retention}/{@code jobs}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {

    /**
     * Thư mục gốc lưu trữ file vật lý (tuyệt đối hoặc tương đối so với working directory khi khởi chạy).
     * Mặc định: {@code ./outputs}.
     */
    private String baseDir;

    /** {@code local} (hiện tại) | {@code s3} (chưa làm). */
    private String backend = "local";

    /** Từ chối upload khi dung lượng trống của kho nhỏ hơn giá trị này ({@code STORAGE_LOW_DISK}). */
    private DataSize minFreeDisk = DataSize.ofGigabytes(5);

    private Defaults defaults = new Defaults();

    private Quota quota = new Quota();

    private Retention retention = new Retention();

    private Scan scan = new Scan();

    private Jobs jobs = new Jobs();

    /** Giới hạn mặc định của module chưa có cấu hình trong DB. */
    @Data
    public static class Defaults {
        private DataSize maxFileSize = DataSize.ofMegabytes(20);
        private int maxFilesPerRef = 30;
        private DataSize maxTotalPerRef = DataSize.ofMegabytes(200);
    }

    /** Quota mặc định khi {@code SYS_FILE_QUOTAS} chưa có dòng. */
    @Data
    public static class Quota {
        private DataSize global = DataSize.ofGigabytes(50);
        private DataSize studentDefault = DataSize.ofMegabytes(500);
        private int warnPercent = 80;
    }

    /** Chính sách lưu giữ (QĐ-F3). */
    @Data
    public static class Retention {
        /** Số ngày file DELETED nằm trong thùng rác trước khi job xoá vĩnh viễn blob. */
        private int trashDays = 30;
        /** File PENDING (upload chưa gắn bản ghi) quá số giờ này bị job xoá. */
        private int pendingHours = 24;
        /** Đối soát bỏ qua blob mới hơn số giờ này (đang upload dở). */
        private int orphanGraceHours = 24;
    }

    /** Quét virus (QĐ-F2). */
    @Data
    public static class Scan {
        /** {@code none} | {@code clamav}. */
        private String mode = "none";
        private ClamAv clamav = new ClamAv();

        @Data
        public static class ClamAv {
            private String host = "clamav";
            private int port = 3310;
            private Duration timeout = Duration.ofSeconds(15);
            /** {@code true}: lỗi quét / chưa quét thì không cho tải. */
            private boolean failClosed = true;
        }
    }

    /** Job nền (Luồng B: {@code FileRetentionJob}). */
    @Data
    public static class Jobs {
        private boolean enabled = true;
        private String retentionCron = "0 30 2 * * *";
    }
}
