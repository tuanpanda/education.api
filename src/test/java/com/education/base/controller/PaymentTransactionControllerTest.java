package com.education.base.controller;

import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDetailResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.service.PaymentService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentTransactionController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class PaymentTransactionControllerTest {

    private static final String BASE = "/api/v1/payments/transactions";
    private static final String NO_PAYMENT_PERMS = "MENU_STUDENT_LIST:VIEW";

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private PaymentService paymentService;

    @Test
    void search_bindsFiltersAndReturnsPage() throws Exception {
        when(paymentService.search(any())).thenReturn(PageResponse.of(List.of(PaymentTransactionDto.builder()
                .id(50L).receiptNo("PT20261000001").transactionType("PAYMENT").status("SUCCESS")
                .amount(new BigDecimal("1500000")).build()), 1, 20, 1));

        mockMvc.perform(get(BASE + "/search")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-31")
                        .param("paymentMethod", "CASH")
                        .param("status", "VOIDED")
                        .param("transactionType", "REFUND")
                        .param("classId", "70")
                        .param("receiptNo", "PT2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.content[0].receiptNo").value("PT20261000001"));

        ArgumentCaptor<com.education.base.dto.request.PaymentTransactionFilterRequest> captor =
                ArgumentCaptor.forClass(com.education.base.dto.request.PaymentTransactionFilterRequest.class);
        verify(paymentService).search(captor.capture());
        assertThat(captor.getValue().getFromDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(captor.getValue().getToDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(captor.getValue().getStatus()).isEqualTo("VOIDED");
        assertThat(captor.getValue().getClassId()).isEqualTo(70L);
    }

    @Test
    void search_invalidStatus_returnsValidationError() throws Exception {
        mockMvc.perform(get(BASE + "/search").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_PAYMENT_HISTORY:VIEW")
    void search_withViewPermission_returns200() throws Exception {
        when(paymentService.search(any())).thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        mockMvc.perform(get(BASE + "/search"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = NO_PAYMENT_PERMS)
    void search_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/search"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void getDetail_returnsTransactionFeeAndRefunds() throws Exception {
        when(paymentService.getDetail(50L)).thenReturn(PaymentTransactionDetailResponse.builder()
                .transaction(PaymentTransactionDto.builder().id(50L).status("SUCCESS").build())
                .fee(PaymentTransactionDetailResponse.FeeSummary.builder().id(4L).feeCode("FEE01").build())
                .refunds(List.of(PaymentTransactionDto.builder().id(60L).transactionType("REFUND").build()))
                .refundable(true)
                .build());

        mockMvc.perform(get(BASE + "/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transaction.id").value(50))
                .andExpect(jsonPath("$.data.fee.feeCode").value("FEE01"))
                .andExpect(jsonPath("$.data.refunds[0].id").value(60))
                .andExpect(jsonPath("$.data.refundable").value(true))
                .andExpect(jsonPath("$.data.voidable").value(false));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = NO_PAYMENT_PERMS)
    void getDetail_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/50"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    void receiptHtml_returnsHtmlDocument() throws Exception {
        when(paymentService.renderReceiptHtml(50L)).thenReturn("<html><body>PHIẾU THU HỌC PHÍ</body></html>");

        mockMvc.perform(get(BASE + "/50/receipt/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("PHIẾU THU HỌC PHÍ")));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = NO_PAYMENT_PERMS)
    void receiptHtml_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/50/receipt/html"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    void void_returnsVoidedTransaction() throws Exception {
        when(paymentService.voidTransaction(eq(50L), any())).thenReturn(PaymentTransactionDto.builder()
                .id(50L).status("VOIDED").voidReason("Nhập nhầm").build());

        mockMvc.perform(post(BASE + "/50/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Nhập nhầm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.status").value("VOIDED"));
    }

    @Test
    void void_withoutReason_returnsValidationError() throws Exception {
        mockMvc.perform(post(BASE + "/50/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = {"MENU_PAYMENT_HISTORY:VIEW", "MENU_PAYMENT_HISTORY:REFUND"})
    void void_withoutVoidPermission_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/50/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_PAYMENT_HISTORY:VOID")
    void void_withVoidPermission_returns200() throws Exception {
        when(paymentService.voidTransaction(eq(50L), any())).thenReturn(PaymentTransactionDto.builder()
                .id(50L).status("VOIDED").build());

        mockMvc.perform(post(BASE + "/50/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refund_returnsRefundRow() throws Exception {
        when(paymentService.refundTransaction(eq(50L), any())).thenReturn(PaymentTransactionDto.builder()
                .id(61L).transactionType("REFUND").refTransactionId(50L).amount(new BigDecimal("400000")).build());

        mockMvc.perform(post(BASE + "/50/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":400000,\"method\":\"CASH\",\"reason\":\"Nghỉ học\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionType").value("REFUND"))
                .andExpect(jsonPath("$.data.refTransactionId").value(50));
    }

    @Test
    void refund_invalidPayload_returnsValidationError() throws Exception {
        mockMvc.perform(post(BASE + "/50/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"method\":\"GOLD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = {"MENU_PAYMENT_HISTORY:VIEW", "MENU_PAYMENT_HISTORY:VOID"})
    void refund_withoutRefundPermission_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/50/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1,\"method\":\"CASH\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_PAYMENT_HISTORY:REFUND")
    void refund_withRefundPermission_returns200() throws Exception {
        when(paymentService.refundTransaction(eq(50L), any())).thenReturn(PaymentTransactionDto.builder()
                .id(61L).build());

        mockMvc.perform(post(BASE + "/50/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1,\"method\":\"CASH\",\"reason\":\"x\"}"))
                .andExpect(status().isOk());
    }
}
