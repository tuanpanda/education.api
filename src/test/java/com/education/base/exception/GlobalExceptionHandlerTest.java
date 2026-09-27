package com.education.base.exception;

import com.education.base.common.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleOracleBusinessException_returnsBadRequestWrappedInApiResponse() {
        OracleBusinessException ex = new OracleBusinessException("1", "Không tìm thấy học sinh.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleOracleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("1");
        assertThat(response.getBody().getMessage()).isEqualTo("Không tìm thấy học sinh.");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void handleMethodArgumentNotValidException_returnsValidationError() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "bankBin", "Mã ngân hàng (BIN) không được để trống"));

        Method method = DummyController.class.getDeclaredMethod("dummy", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValidException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).contains("bankBin");
    }

    @Test
    void handleConstraintViolationException_returnsValidationError() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<DummyValidated>> violations = validator.validate(new DummyValidated(""));
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).contains("keyword");
    }

    @Test
    void handleMaxUploadSizeExceededException_returnsPayloadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(10));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void handleDataAccessException_returnsInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataAccessException(new DataAccessResourceFailureException("down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DATA_ACCESS_ERROR");
    }

    @Test
    void handleNoResourceFoundException_returnsNotFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "swagger");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResourceFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("swagger");
    }

    @Test
    void handleNoHandlerFoundException_returnsNotFound() {
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/unknown", new org.springframework.http.HttpHeaders());

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoHandlerFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("/unknown");
    }

    @Test
    void handleGenericException_returnsInternalErrorWithoutLeakingDetails() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGenericException(new RuntimeException("secret stack"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("secret stack");
    }

    @SuppressWarnings("unused")
    private static class DummyController {
        public void dummy(String value) {
        }
    }

    private record DummyValidated(@NotBlank(message = "không được trống") String keyword) {
    }
}
