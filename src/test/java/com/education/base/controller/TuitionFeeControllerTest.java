package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.service.TuitionFeeService;
import com.education.base.service.TuitionSlipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TuitionFeeController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class TuitionFeeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private TuitionFeeService tuitionFeeService;
    @MockBean
    private TuitionSlipService tuitionSlipService;

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

    @Test
    void generateMonthly_returnsCreatedSlips() throws Exception {
        when(tuitionSlipService.generateMonthly(any())).thenReturn(GenerateMonthlyInvoicesResponseDto.builder()
                .createdCount(1)
                .updatedCount(0)
                .skippedNoAttendance(0)
                .slips(List.of(TuitionSlipResponseDto.builder()
                        .invoiceId(9L)
                        .invoiceCode("HP2026040001")
                        .totalSessions(12)
                        .totalAmount(new BigDecimal("960000"))
                        .build()))
                .build());

        mockMvc.perform(post("/api/v1/tuition-fees/generate-monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":3,\"month\":4,\"year\":2026,\"pricePerSession\":80000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.slips[0].invoiceId").value(9));
    }

    @Test
    void getSlip_returnsJsonCard() throws Exception {
        when(tuitionSlipService.getSlip(9L)).thenReturn(TuitionSlipResponseDto.builder()
                .invoiceId(9L)
                .studentName("Nguyen Van A")
                .totalAmount(new BigDecimal("960000"))
                .attendedDates(List.of("02/04", "04/04"))
                .qrBase64("data:image/png;base64,abc")
                .build());

        mockMvc.perform(get("/api/v1/tuition-fees/9/slip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceId").value(9))
                .andExpect(jsonPath("$.data.attendedDates[0]").value("02/04"));
    }

    @Test
    void getSlipHtml_returnsHtmlDocument() throws Exception {
        when(tuitionSlipService.generateSlipHtml(9L)).thenReturn("<html><body>PHIẾU HỌC PHÍ</body></html>");

        mockMvc.perform(get("/api/v1/tuition-fees/9/slip/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PHIẾU HỌC PHÍ")));
    }
}
