package com.education.base.controller;

import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.service.TuitionFeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TuitionFeeController.class)
class TuitionFeeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private TuitionFeeService tuitionFeeService;

    @Test
    void createQr_returnsPayloadAndImage() throws Exception {
        when(tuitionFeeService.createQr(eq(4L), any())).thenReturn(TuitionQrResponseDto.builder()
                .tuitionFeeId(4L)
                .feeCode("FEE01")
                .remainingAmount(new BigDecimal("1500000"))
                .qrPayload("000201")
                .base64Image("data:image/png;base64,abc")
                .quickUrl("https://img.vietqr.io/image/970436-1234567890-compact2.png")
                .build());

        mockMvc.perform(post("/api/v1/tuition-fees/4/create-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.qrPayload").value("000201"))
                .andExpect(jsonPath("$.data.base64Image").value("data:image/png;base64,abc"));
    }

    @Test
    void confirmPayment_returnsTransaction() throws Exception {
        when(tuitionFeeService.confirmPayment(eq(4L), any())).thenReturn(PaymentTransactionDto.builder()
                .id(33L)
                .transactionCode("PAY4")
                .amount(new BigDecimal("1500000"))
                .status("SUCCESS")
                .paymentMethod("VIETQR")
                .build());

        mockMvc.perform(post("/api/v1/tuition-fees/4/confirm-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"VIETQR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
}
