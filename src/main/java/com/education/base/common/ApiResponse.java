package com.education.base.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Cấu trúc phản hồi chuẩn hóa cho toàn bộ API của hệ thống EDUCATION.
 * Mọi Controller trả dữ liệu JSON ra ngoài đều phải bọc qua {@link ApiResponse}.
 *
 * @param <T> kiểu dữ liệu payload trả về trong trường {@code data}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    /**
     * Mã kết quả xử lý. Quy ước: {@code "00"} là thành công, các mã khác là mã lỗi nghiệp vụ/hệ thống.
     */
    private String code;

    /**
     * Thông điệp mô tả kết quả xử lý, dùng để hiển thị cho client hoặc ghi log.
     */
    private String message;

    /**
     * Dữ liệu trả về của API. Có thể là {@code null} khi xảy ra lỗi.
     */
    private T data;

    /**
     * Thời điểm server xử lý và tạo ra response.
     */
    private LocalDateTime timestamp;

    public static final String SUCCESS_CODE = "00";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "SUCCESS", data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS_CODE, message, data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, LocalDateTime.now());
    }
}
