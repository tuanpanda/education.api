package com.education.base.controller;

import com.education.base.dto.request.GenerateQrRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QrPaymentController.class)
class QrPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generateQr_validRequest_returnsQuickUrlPayloadAndImage() throws Exception {
        GenerateQrRequest request = new GenerateQrRequest();
        request.setBankBin("970436");
        request.setAccountNo("1234567890");
        request.setAccountName("NGUYEN VAN A");
        request.setAmount(150000L);
        request.setDescription("HOC PHI");

        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.message").value("Tạo mã QR thành công."))
                .andExpect(jsonPath("$.data.qrPayload").value(startsWith("000201010212")))
                .andExpect(jsonPath("$.data.qrPayload").value(containsString("5406150000")))
                .andExpect(jsonPath("$.data.quickUrl").value(containsString("img.vietqr.io")))
                .andExpect(jsonPath("$.data.quickUrl").value(containsString("amount=150000")))
                .andExpect(jsonPath("$.data.base64Image").value(startsWith("data:image/png;base64,")));
    }

    @Test
    void generateQr_withoutAmount_returnsStaticQrWithoutAmountParam() throws Exception {
        String body = """
                {
                  "bankBin": "970436",
                  "accountNo": "1234567890"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrPayload").value(startsWith("000201010211")))
                .andExpect(jsonPath("$.data.quickUrl")
                        .value("https://img.vietqr.io/image/970436-1234567890-compact2.png"));
    }

    @Test
    void generateQr_missingBankBin_returnsValidationError() throws Exception {
        String body = """
                {
                  "bankBin": "",
                  "accountNo": "1234567890"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("bankBin")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void generateQr_nonPositiveAmount_returnsValidationError() throws Exception {
        String body = """
                {
                  "bankBin": "970436",
                  "accountNo": "1234567890",
                  "amount": 0
                }
                """;

        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void generateQr_malformedJson_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not-json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
