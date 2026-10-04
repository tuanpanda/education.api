package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.dto.request.GenerateQrRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.service.BankAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QrPaymentController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class QrPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BankAccountService bankAccountService;

    @BeforeEach
    void stubActiveAccount() {
        when(bankAccountService.requireActive()).thenReturn(BankAccountResponseDto.builder()
                .bankBin("970436")
                .bankName("Vietcombank")
                .accountNo("1234567890")
                .accountName("TRUONG EDUCATION")
                .active(true)
                .build());
    }

    @Test
    void generateQr_validRequest_returnsQuickUrlPayloadAndImage() throws Exception {
        GenerateQrRequest request = new GenerateQrRequest();
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
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:GEN_QR")
    void generateQr_withTuitionFeeGenQr_isAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrPayload").value(startsWith("000201")));
    }

    /** V16: MENU_TUITION_PAYMENT (menu "Thu học phí VietQR") đã gỡ; quyền cũ không còn sinh QR. */
    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_PAYMENT:GEN_QR")
    void generateQr_withRemovedVietQrMenuPermission_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verify(bankAccountService, never()).requireActive();
    }

    @Test
    void generateQr_withoutAmount_returnsStaticQrWithoutAmountParam() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrPayload").value(startsWith("000201010211")))
                .andExpect(jsonPath("$.data.quickUrl").value(containsString("970436-1234567890-compact2.png")))
                .andExpect(jsonPath("$.data.quickUrl").value(containsString("accountName=TRUONG")));
    }

    @Test
    void generateQr_ignoresClientBankFields_usesActiveAccount() throws Exception {
        String body = """
                {
                  "bankBin": "970415",
                  "accountNo": "9999999999",
                  "amount": 150000
                }
                """;

        mockMvc.perform(post("/api/v1/payments/generate-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quickUrl").value(containsString("970436-1234567890")));
    }

    @Test
    void generateQr_nonPositiveAmount_returnsValidationError() throws Exception {
        String body = """
                {
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
