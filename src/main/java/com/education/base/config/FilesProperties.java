package com.education.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình hành vi nghiệp vụ file ({@code app.files} trong {@code application.yml}). Khung hợp đồng Luồng 0.
 * Chỉ Luồng A sửa lớp này.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.files")
public class FilesProperties {

    private Audit audit = new Audit();

    /**
     * Giới hạn giáo viên theo lớp dạy với file STUDENT/CLASS (QĐ-F12). Cờ khẩn cấp để tắt nếu thay đổi gây vướng
     * nghiệp vụ (rủi ro 7.2).
     */
    private boolean teacherScopeEnabled = true;

    @Data
    public static class Audit {
        /** Ghi {@code FILE_VIEWED} cho mọi lần xem inline (QĐ-F11: mặc định tắt). */
        private boolean views = false;
    }
}
