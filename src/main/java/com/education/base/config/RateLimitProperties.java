package com.education.base.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Giới hạn tần suất tổng quát theo IP / theo người dùng, tiền tố {@code app.security.rate-limit}
 * (áp dụng bởi {@code RequestRateLimitFilter}). Đăng nhập / làm mới token vẫn dùng
 * {@code app.security.auth.rate-limit} (cần đọc tên đăng nhập trong body / refresh token trong cookie).
 * <p>
 * Mỗi quy tắc ({@link #rules}, khóa = tên quy tắc) khớp theo đường dẫn (Ant pattern), method HTTP và (tùy chọn) chỉ
 * request multipart. Một request có thể khớp nhiều quy tắc: phải qua TẤT CẢ. Vượt ngưỡng -> HTTP 429 kèm
 * {@code Retry-After}. Giới hạn theo người dùng chỉ áp cho request đã đăng nhập (khóa = {@code SYS_USERS.ID}).
 * Giá trị {@code 0} = tắt chiều giới hạn đó. Mặc định khai báo trong {@code application.yml} (biến môi trường
 * {@code RATE_LIMIT_*}).
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    /** Tắt toàn bộ quy tắc tổng quát (không ảnh hưởng giới hạn đăng nhập / refresh). */
    private boolean enabled = true;

    /** Số khóa tối đa được theo dõi trong bộ nhớ (dùng chung với giới hạn đăng nhập). */
    @Min(100)
    private int maxTrackedKeys = 100_000;

    @Valid
    @NotNull
    private Map<String, Rule> rules = new LinkedHashMap<>();

    @Data
    public static class Rule {

        private boolean enabled = true;

        /** Ant pattern tính từ context path, ví dụ {@code /api/v1/portal/**}. Rỗng = quy tắc không khớp gì. */
        @NotNull
        private List<String> paths = new ArrayList<>();

        /** Method HTTP áp dụng (không phân biệt hoa thường); rỗng = mọi method trừ OPTIONS. */
        @NotNull
        private List<String> methods = new ArrayList<>();

        /** Chỉ áp cho request {@code multipart/*} (upload file). */
        private boolean multipartOnly;

        @NotNull
        private Duration window = Duration.ofMinutes(5);

        /** Số request tối đa mỗi IP trong một cửa sổ; 0 = không giới hạn theo IP. */
        @Min(0)
        private int perIp;

        /** Số request tối đa mỗi người dùng đã đăng nhập trong một cửa sổ; 0 = không giới hạn theo người dùng. */
        @Min(0)
        private int perUser;
    }
}
