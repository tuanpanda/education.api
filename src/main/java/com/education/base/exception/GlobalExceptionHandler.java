package com.education.base.exception;

import com.education.base.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xử lý tập trung toàn bộ Exception phát sinh trong hệ thống EDUCATION,
 * đảm bảo mọi lỗi trả về client đều được bọc qua {@link ApiResponse}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Xử lý lỗi nghiệp vụ, bao gồm cả lỗi trả về từ Oracle Package/Procedure
     * (thông qua {@code OracleProcExecutor.validateResult}).
     * <ul>
     *     <li>Mã lỗi {@code NOT_FOUND} / {@code *_NOT_FOUND} trả HTTP 404, còn lại HTTP 400.</li>
     *     <li>Lỗi nghiệp vụ ghi log WARN, không kèm stack trace.</li>
     *     <li>Message là lỗi Oracle/JDBC thô ({@code ORA-xxxxx}...) được thay bằng thông báo chung
     *     ({@link OracleErrorMessages#GENERIC_MESSAGE}), chi tiết ghi log ERROR; {@code ORA-20xxx: text}
     *     chỉ trả {@code text}.</li>
     * </ul>
     */
    @ExceptionHandler(OracleBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleOracleBusinessException(OracleBusinessException ex) {
        HttpStatus status = isNotFoundCode(ex.getErrorCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String rawMessage = ex.getMessage();
        if (OracleErrorMessages.isRawDatabaseError(rawMessage)) {
            log.error("[OracleBusinessException] errorCode={}, message={}", ex.getErrorCode(), rawMessage, ex);
        } else {
            log.warn("[BusinessException] errorCode={}, status={}, message={}",
                    ex.getErrorCode(), status.value(), rawMessage);
        }
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getErrorCode(), OracleErrorMessages.toClientMessage(rawMessage)));
    }

    /**
     * Xử lý lỗi validate dữ liệu đầu vào ({@code @Valid} trên request body).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + describeFieldError(fe))
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Dữ liệu đầu vào không hợp lệ.";
        }
        log.warn("[ValidationError] {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * Xử lý lỗi validate ở mức tham số (path/query param, {@code @Validated}).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("[ConstraintViolation] {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * Thiếu tham số bắt buộc trên query string (ví dụ: {@code userId}).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        String message = "Thiếu tham số bắt buộc: " + ex.getParameterName();
        log.warn("[MissingParameter] {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * Thiếu phần bắt buộc trong request multipart (ví dụ: {@code file}).
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPartException(
            MissingServletRequestPartException ex) {
        String message = "Thiếu phần dữ liệu bắt buộc: " + ex.getRequestPartName();
        log.warn("[MissingRequestPart] {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * Tham số truyền vào sai kiểu dữ liệu (ví dụ: {@code userId=abc}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex) {
        String message = "Tham số '" + ex.getName() + "' có giá trị không hợp lệ.";
        log.warn("[TypeMismatch] {}: {}", message, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * Request body không đọc được (JSON sai cú pháp hoặc sai kiểu dữ liệu).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        log.warn("[MessageNotReadable] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", "Dữ liệu đầu vào không đọc được hoặc sai định dạng JSON."));
    }

    /**
     * Phương thức HTTP không được hỗ trợ trên URL (ví dụ: {@code DELETE} trên endpoint chỉ có {@code GET}).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("[MethodNotSupported] {}", ex.getMessage());
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(HttpMethod[]::new));
        }
        return builder.body(ApiResponse.error("METHOD_NOT_ALLOWED",
                "Phương thức " + ex.getMethod() + " không được hỗ trợ cho đường dẫn này."));
    }

    /**
     * {@code Content-Type} của request không được hỗ trợ (ví dụ: gửi {@code text/plain} tới API JSON).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex) {
        log.warn("[MediaTypeNotSupported] {}", ex.getMessage());
        String contentType = ex.getContentType() != null ? ex.getContentType().toString() : "(không có)";
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("UNSUPPORTED_MEDIA_TYPE",
                        "Kiểu dữ liệu (Content-Type) không được hỗ trợ: " + contentType));
    }

    /**
     * Xử lý lỗi khi file upload vượt quá kích thước cho phép (multipart).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("[MaxUploadSizeExceeded] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("FILE_TOO_LARGE", "Kích thước file vượt quá giới hạn cho phép."));
    }

    /**
     * Xử lý lỗi truy cập dữ liệu (JDBC/JPA) chưa được bọc thành {@link OracleBusinessException}.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException ex) {
        log.error("[DataAccessException] {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("DATA_ACCESS_ERROR", "Lỗi truy cập dữ liệu tại Database."));
    }

    /**
     * URL không khớp controller và cũng không có static resource (ví dụ: /swagger khi chưa redirect).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("[NoResourceFound] {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "Không tìm thấy tài nguyên: " + ex.getResourcePath()));
    }

    /**
     * Không có handler cho URL/method được yêu cầu.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(NoHandlerFoundException ex) {
        log.warn("[NoHandlerFound] {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "Không tìm thấy tài nguyên: " + ex.getRequestURL()));
    }

    /**
     * Lỗi xác thực: sai thông tin đăng nhập, token hết hạn/không hợp lệ, tài khoản bị khóa.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.warn("[Unauthorized] errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Lỗi xác thực phát sinh từ Spring Security bên trong Controller.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("[AuthenticationException] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục."));
    }

    /**
     * Người dùng không đủ quyền thực hiện chức năng (kiểm tra bởi {@code PermissionInterceptor}).
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(ForbiddenException ex) {
        log.warn("[Forbidden] errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Từ chối truy cập từ Spring Security (method security).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("[AccessDenied] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "Bạn không có quyền thực hiện chức năng này."));
    }

    /**
     * Xử lý toàn bộ lỗi hệ thống chưa được định danh cụ thể (fallback cuối cùng).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("[UnhandledException] {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Đã có lỗi xảy ra, vui lòng thử lại sau."));
    }

    /**
     * Mã lỗi nghiệp vụ biểu diễn "không tìm thấy" ({@code NOT_FOUND}, {@code STUDENT_NOT_FOUND}, {@code FEE_NOT_FOUND}...).
     */
    static boolean isNotFoundCode(String errorCode) {
        return errorCode != null && ("NOT_FOUND".equals(errorCode) || errorCode.endsWith("_NOT_FOUND"));
    }

    private String describeFieldError(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Giá trị không hợp lệ";
    }
}
