package com.education.base.exception;

import lombok.Getter;

/**
 * Lỗi nghiệp vụ kèm {@code data} trong {@code ApiResponse} (ví dụ danh sách lỗi theo dòng khi nhập Excel).
 * HTTP như {@link OracleBusinessException} (400 / 404 theo mã).
 */
@Getter
public class BusinessDataException extends OracleBusinessException {

    private final transient Object data;

    public BusinessDataException(String errorCode, String message, Object data) {
        super(errorCode, message);
        this.data = data;
    }
}
