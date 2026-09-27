package com.education.base.exception;

import lombok.Getter;

/**
 * Exception nghiệp vụ dùng để biểu diễn lỗi trả về từ Oracle Package/Procedure
 * (thông qua {@code O_ERR_CODE} / {@code O_ERR_MSG}) hoặc lỗi nghiệp vụ tại tầng Service/Repository.
 * <p>
 * Exception này được xử lý tập trung tại {@link GlobalExceptionHandler}.
 */
@Getter
public class OracleBusinessException extends RuntimeException {

    /**
     * Mã lỗi nghiệp vụ. Có thể là mã lỗi trả về từ Oracle ({@code O_ERR_CODE})
     * hoặc mã lỗi tự định nghĩa tại tầng Java (ví dụ: {@code "FILE_NOT_FOUND"}).
     */
    private final String errorCode;

    public OracleBusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OracleBusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
