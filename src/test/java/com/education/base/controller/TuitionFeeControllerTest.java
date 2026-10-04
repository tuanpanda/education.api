package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    // ---- V16: menu "Thu học phí VietQR" (MENU_TUITION_PAYMENT) bị gỡ, GEN_QR chuyển sang MENU_TUITION_FEE --------

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:GEN_QR")
    void createQr_withTuitionFeeGenQr_isAllowed() throws Exception {
        when(tuitionFeeService.createQr(eq(4L), any())).thenReturn(TuitionQrResponseDto.builder()
                .tuitionFeeId(4L)
                .qrPayload("000201")
                .build());

        mockMvc.perform(post("/api/v1/tuition-fees/4/create-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrPayload").value("000201"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = {"MENU_TUITION_PAYMENT:VIEW", "MENU_TUITION_PAYMENT:GEN_QR"})
    void removedVietQrMenuPermissions_noLongerOpenFees() throws Exception {
        mockMvc.perform(post("/api/v1/tuition-fees/4/create-qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tuition-fees/search")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tuition-fees/4")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tuition-fees/9/slip")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tuition-fees/9/slip/html")).andExpect(status().isForbidden());
        verifyNoInteractions(tuitionFeeService, tuitionSlipService);
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

    @Test
    void generateMonthly_returnsConflictsAndErrors() throws Exception {
        when(tuitionSlipService.generateMonthly(any())).thenReturn(GenerateMonthlyInvoicesResponseDto.builder()
                .updatedCount(0)
                .cancelledCount(1)
                .conflicts(List.of(GenerateMonthlyInvoicesResponseDto.FeeConflict.builder()
                        .feeId(50L)
                        .reason(GenerateMonthlyInvoicesResponseDto.CONFLICT_NET_BELOW_PAID)
                        .paidAmount(new BigDecimal("200000"))
                        .build()))
                .errors(List.of(GenerateMonthlyInvoicesResponseDto.StudentError.builder()
                        .studentId(8L).code("FEE_LOCKED").build()))
                .build());

        mockMvc.perform(post("/api/v1/tuition-fees/generate-monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":3,\"month\":4,\"year\":2026,\"pricePerSession\":80000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelledCount").value(1))
                .andExpect(jsonPath("$.data.conflicts[0].feeId").value(50))
                .andExpect(jsonPath("$.data.conflicts[0].reason").value("NET_BELOW_PAID"))
                .andExpect(jsonPath("$.data.errors[0].code").value("FEE_LOCKED"));
    }

    // ---- B9: MENU_DASHBOARD:VIEW không còn mở được dữ liệu khoản phí -----------------------------------

    @Test
    @WithAuthUser(roles = "ROLE_ADMISSION", permissions = {"MENU_DASHBOARD:VIEW", "MENU_LEAD_LIST:VIEW"})
    void feeReads_withOnlyDashboardView_areForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/tuition-fees/search")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tuition-fees/9")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tuition-fees/9/slip")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tuition-fees/9/slip/html")).andExpect(status().isForbidden());
        verifyNoInteractions(tuitionFeeService, tuitionSlipService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:VIEW")
    void feeReads_withTuitionFeeView_areAllowed() throws Exception {
        when(tuitionFeeService.search(any())).thenReturn(PageResponse.of(List.of(), 1, 20, 0));
        when(tuitionSlipService.getSlip(9L)).thenReturn(TuitionSlipResponseDto.builder().invoiceId(9L).build());
        when(tuitionSlipService.generateSlipHtml(9L)).thenReturn("<html></html>");

        mockMvc.perform(get("/api/v1/tuition-fees/search")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tuition-fees/9/slip")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tuition-fees/9/slip/html")).andExpect(status().isOk());
    }

    @Test
    @WithAuthUser(roles = "ROLE_CASHIER", permissions = {"MENU_PAYMENT_HISTORY:VIEW"})
    void feeSearch_withPaymentHistoryView_isAllowed() throws Exception {
        when(tuitionFeeService.search(any())).thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/tuition-fees/search").param("studentStatus", "INACTIVE"))
                .andExpect(status().isOk());
    }

    @Test
    void feeSearch_invalidStudentStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tuition-fees/search").param("studentStatus", "GONE"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tuitionFeeService);
    }
    // ---- Vòng đời khoản phí: sửa / hủy / xóa ----------------------------------------------------------

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:UPDATE")
    void update_withUpdatePermission_returnsDetail() throws Exception {
        when(tuitionFeeService.update(eq(4L), any())).thenReturn(TuitionFeeDetailResponse.builder()
                .id(4L).status("UNPAID").totalAmount(new BigDecimal("900000")).build());

        mockMvc.perform(put("/api/v1/tuition-fees/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalAmount\":900000,\"discountAmount\":0,\"note\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(900000));
    }

    @Test
    void update_missingTotal_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/tuition-fees/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountAmount\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tuitionFeeService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:CANCEL")
    void cancel_withCancelPermission_returnsCancelledDetail() throws Exception {
        when(tuitionFeeService.cancel(eq(4L), any())).thenReturn(TuitionFeeDetailResponse.builder()
                .id(4L).status("CANCELLED").cancelReason("Nghỉ học").build());

        mockMvc.perform(post("/api/v1/tuition-fees/4/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Nghỉ học\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Nghỉ học"));
    }

    @Test
    void cancel_blankReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tuition-fees/4/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tuitionFeeService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:DELETE")
    void delete_withDeletePermission_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/tuition-fees/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"));
        verify(tuitionFeeService).delete(4L);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = {"MENU_TUITION_FEE:VIEW", "MENU_TUITION_FEE:CREATE"})
    void lifecycle_withoutMatchingPermission_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/tuition-fees/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalAmount\":900000}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tuition-fees/4/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/tuition-fees/4")).andExpect(status().isForbidden());
        verifyNoInteractions(tuitionFeeService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:CREATE")
    void create_withoutFeeCode_isAccepted() throws Exception {
        when(tuitionFeeService.create(any())).thenReturn(TuitionFeeDetailResponse.builder()
                .id(40L).feeCode("HP2026100001").build());

        mockMvc.perform(post("/api/v1/tuition-fees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":8,\"totalAmount\":1000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeCode").value("HP2026100001"));
    }
}
