package com.education.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình lưu trữ file vật lý của hệ thống, ánh xạ từ tiền tố {@code app.storage} trong
 * {@code application.yml}.
 * <p>
 * File vật lý được lưu tại {@code {baseDir}/{MODULE}/{YYYY}/{MM}/{UUID}_{tenfile}}.
 * Database chỉ lưu đường dẫn tương đối so với {@code baseDir}.
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
}
