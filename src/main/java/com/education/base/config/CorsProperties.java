package com.education.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * Cấu hình CORS ({@code app.cors.*}).
 * <p>
 * {@code app.cors.allowed-origins} lấy từ biến môi trường {@code CORS_ALLOWED_ORIGINS}
 * (danh sách origin phân tách bằng dấu phẩy, ví dụ {@code http://localhost:5173,https://edu.example.vn}).
 * Mỗi phần tử là origin chính xác hoặc pattern có {@code *} (ví dụ {@code http://192.168.1.*:8088}).
 * Vì CORS bật {@code allowCredentials} (cookie phiên), pattern khớp MỌI host ({@code *}, {@code http://*},
 * {@code https://*:8088}...) bị từ chối ngay khi khởi động.
 * Danh sách rỗng = không bật CORS, chỉ phục vụ same-origin (UI Docker gọi {@code /api} qua nginx cùng origin).
 *
 * @param allowedOrigins danh sách origin được phép gọi API cross-origin.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
        allowedOrigins.forEach(CorsProperties::rejectWildcardHost);
    }

    private static void rejectWildcardHost(String origin) {
        String rest = origin;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        }
        int end = rest.length();
        for (char separator : new char[]{':', '/'}) {
            int index = rest.indexOf(separator);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        String host = rest.substring(0, end);
        if (host.isEmpty() || host.chars().allMatch(c -> c == '*')) {
            throw new IllegalArgumentException("app.cors.allowed-origins (CORS_ALLOWED_ORIGINS) không được chứa origin "
                    + "khớp mọi host ('" + origin + "'): CORS gửi kèm cookie đăng nhập, hãy liệt kê origin cụ thể.");
        }
    }
}
