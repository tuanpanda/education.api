package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.common.DomainConstants;
import com.education.base.dto.response.BillableSessionsPreviewDto;
import com.education.base.dto.response.FeeSessionConflictDto;
import com.education.base.dto.response.GenerateInvoicesResponseDto;
import com.education.base.exception.BusinessDataException;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.time.LocalDate;
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

    // ---- Thu theo khoảng ngày (contract fee-range-api v1.3) -----------------------------------------------

    private static final String RANGE_BODY =
            "{\"classId\":3,\"fromDate\":\"2026-10-01\",\"toDate\":\"2026-10-31\",\"pricePerSession\":80000}";

    @Test
    void generateRange_created_returns201WithFeeIds() throws Exception {
        when(tuitionSlipService.generateRange(any())).thenReturn(GenerateInvoicesResponseDto.builder()
                .createdCount(2).skippedNoAttendance(1).feeIds(new java.util.ArrayList<>(List.of(9L, 10L))).build());

        mockMvc.perform(post("/api/v1/tuition-fees/generate-range")
                        .contentType(MediaType.APPLICATION_JSON).content(RANGE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.createdCount").value(2))
                .andExpect(jsonPath("$.data.skippedNoAttendance").value(1))
                .andExpect(jsonPath("$.data.feeIds[1]").value(10));
    }

    @Test
    void generateRange_nothingCreated_returns200() throws Exception {
        when(tuitionSlipService.generateRange(any())).thenReturn(GenerateInvoicesResponseDto.builder()
                .createdCount(0).skippedNoAttendance(3).build());

        mockMvc.perform(post("/api/v1/tuition-fees/generate-range")
                        .contentType(MediaType.APPLICATION_JSON).content(RANGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(0))
                .andExpect(jsonPath("$.data.feeIds").isEmpty());
    }

    @Test
    void generateRange_conflict_returns409WithDetails() throws Exception {
        when(tuitionSlipService.generateRange(any())).thenThrow(new BusinessDataException(
                DomainConstants.FEE_SESSION_CONFLICT, DomainConstants.FEE_SESSION_CONFLICT_MESSAGE,
                new FeeSessionConflictDto.Payload(List.of(FeeSessionConflictDto.builder()
                        .studentId(8L).studentCode("HS01").studentName("A")
                        .dates(List.of(LocalDate.of(2026, 10, 9))).feeCodes(List.of("HP-000812")).build()))));

        mockMvc.perform(post("/api/v1/tuition-fees/generate-range")
                        .contentType(MediaType.APPLICATION_JSON).content(RANGE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEE_SESSION_CONFLICT"))
                .andExpect(jsonPath("$.data.details[0].studentId").value(8))
                .andExpect(jsonPath("$.data.details[0].dates[0]").value("2026-10-09"))
                .andExpect(jsonPath("$.data.details[0].feeCodes[0]").value("HP-000812"));
    }

    @Test
    void generateRange_uniqueViolationEscapingService_returns409EmptyDetails() throws Exception {
        when(tuitionSlipService.generateRange(any())).thenThrow(new DataIntegrityViolationException("x",
                new RuntimeException("ORA-00001: unique constraint (EDU.UQ_FEE_SESSIONS_ATTENDANCE) violated")));

        mockMvc.perform(post("/api/v1/tuition-fees/generate-range")
                        .contentType(MediaType.APPLICATION_JSON).content(RANGE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEE_SESSION_CONFLICT"))
                .andExpect(jsonPath("$.data.details").isArray())
                .andExpect(jsonPath("$.data.details").isEmpty());
    }

    @Test
    void generateRange_otherIntegrityViolation_returns500() throws Exception {
        when(tuitionSlipService.generateRange(any())).thenThrow(new DataIntegrityViolationException(
                "ORA-02291: integrity constraint (EDU.FK_X) violated"));

        mockMvc.perform(post("/api/v1/tuition-fees/generate-range")
                        .contentType(MediaType.APPLICATION_JSON).content(RANGE_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_ACCESS_ERROR"));
    }

    @Test
    void generateRange_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tuition-fees/generate-range")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":3,\"fromDate\":\"2026-10-01\",\"toDate\":\"2026-10-31\",\"pricePerSession\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/tuition-fees/generate-range")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":3,\"toDate\":\"2026-10-31\",\"pricePerSession\":1000}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tuitionSlipService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:VIEW")
    void generateRangeAndPreview_withoutCreatePermission_areForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/tuition-fees/generate-range")
                        .contentType(MediaType.APPLICATION_JSON).content(RANGE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tuition-fees/billable-sessions").param("classId", "3")
                        .param("fromDate", "2026-10-01").param("toDate", "2026-10-31"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tuitionSlipService);
    }

    @Test
    void generateMonthly_removed_returns405() throws Exception {
        mockMvc.perform(post("/api/v1/tuition-fees/generate-monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":3,\"month\":4,\"year\":2026,\"pricePerSession\":80000}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(tuitionSlipService);
    }

    @Test
    void previewBillable_withoutPrice_moneyFieldsAreJsonNull() throws Exception {
        when(tuitionSlipService.previewBillable(3L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null))
                .thenReturn(BillableSessionsPreviewDto.builder()
                        .classId(3L).fromDate(LocalDate.of(2026, 10, 1)).toDate(LocalDate.of(2026, 10, 31))
                        .students(List.of(BillableSessionsPreviewDto.StudentRow.builder()
                                .studentId(8L).studentCode("HS01").sessionDates(List.of(LocalDate.of(2026, 10, 2)))
                                .sessionCount(1).conflictDates(List.of()).conflictFeeCodes(List.of())
                                .inactive(true).build()))
                        .totals(BillableSessionsPreviewDto.Totals.builder().studentCount(1).billableStudentCount(1)
                                .sessionCount(1).build())
                        .build());

        mockMvc.perform(get("/api/v1/tuition-fees/billable-sessions").param("classId", "3")
                        .param("fromDate", "2026-10-01").param("toDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students[0].inactive").value(true))
                .andExpect(jsonPath("$.data.students[0].sessionDates[0]").value("2026-10-02"))
                // exists() của jsonPath coi null là không có: kiểm tra chuỗi JSON để chắc trường có mặt với giá trị null
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"pricePerSession\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"totalAmount\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"netAmount\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"discountAmount\":null")));
    }

    @Test
    void previewBillable_passesPriceThrough() throws Exception {
        when(tuitionSlipService.previewBillable(eq(3L), any(), any(), eq(new BigDecimal("150000"))))
                .thenReturn(BillableSessionsPreviewDto.builder().classId(3L).students(List.of()).build());

        mockMvc.perform(get("/api/v1/tuition-fees/billable-sessions").param("classId", "3")
                        .param("fromDate", "2026-10-01").param("toDate", "2026-10-31")
                        .param("pricePerSession", "150000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classId").value(3));
    }

    @Test
    void getSlip_rangeFee_exposesPeriodAndRangeFlag() throws Exception {
        when(tuitionSlipService.getSlip(9L)).thenReturn(TuitionSlipResponseDto.builder()
                .invoiceId(9L).periodFrom(LocalDate.of(2026, 10, 1)).periodTo(LocalDate.of(2026, 10, 31))
                .periodText("Từ 01/10/2026 đến 31/10/2026").rangeFee(true).attendedDates(List.of("02/10")).build());

        mockMvc.perform(get("/api/v1/tuition-fees/9/slip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodFrom").value("2026-10-01"))
                .andExpect(jsonPath("$.data.periodText").value("Từ 01/10/2026 đến 31/10/2026"))
                .andExpect(jsonPath("$.data.rangeFee").value(true))
                .andExpect(jsonPath("$.data.attendedDates[0]").value("02/10"));
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
