package com.education.base.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_wrapsPayloadWithStandardSuccessCode() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.getCode()).isEqualTo(ApiResponse.SUCCESS_CODE);
        assertThat(response.getMessage()).isEqualTo("SUCCESS");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void success_withCustomMessage_keepsSuccessCode() {
        ApiResponse<Integer> response = ApiResponse.success("Tạo thành công.", 1);

        assertThat(response.getCode()).isEqualTo("00");
        assertThat(response.getMessage()).isEqualTo("Tạo thành công.");
        assertThat(response.getData()).isEqualTo(1);
    }

    @Test
    void error_setsCodeAndMessageWithoutData() {
        ApiResponse<Void> response = ApiResponse.error("FILE_NOT_FOUND", "Không tìm thấy file.");

        assertThat(response.getCode()).isEqualTo("FILE_NOT_FOUND");
        assertThat(response.getMessage()).isEqualTo("Không tìm thấy file.");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void error_withData_keepsPayload() {
        ApiResponse<String> response = ApiResponse.error("E01", "Lỗi", "chi tiết");

        assertThat(response.getCode()).isEqualTo("E01");
        assertThat(response.getData()).isEqualTo("chi tiết");
    }
}
