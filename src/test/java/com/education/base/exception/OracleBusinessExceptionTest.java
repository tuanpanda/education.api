package com.education.base.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OracleBusinessExceptionTest {

    @Test
    void constructor_keepsErrorCodeAndMessage() {
        OracleBusinessException ex = new OracleBusinessException("FILE_NOT_FOUND", "Không tìm thấy file.");

        assertThat(ex.getErrorCode()).isEqualTo("FILE_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Không tìm thấy file.");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void constructor_withCause_preservesCause() {
        IllegalStateException cause = new IllegalStateException("io");
        OracleBusinessException ex = new OracleBusinessException("FILE_STORE_ERROR", "Không lưu được.", cause);

        assertThat(ex.getErrorCode()).isEqualTo("FILE_STORE_ERROR");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
