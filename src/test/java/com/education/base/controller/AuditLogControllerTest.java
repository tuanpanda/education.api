package com.education.base.controller;

import com.education.base.dto.request.AuditLogFilterRequest;
import com.education.base.dto.response.AuditLogResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.security.Permissions;
import com.education.base.service.AuditLogQueryService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@Import(WebMvcSecurityTestConfig.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AuditLogQueryService auditLogQueryService;

    @Test
    @WithAnonymousUser
    void anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/search"))
                .andExpect(status().isUnauthorized());
        verify(auditLogQueryService, never()).search(any());
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = "MENU_TUITION_FEE:VIEW")
    void userWithoutAuditPermission_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/search"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(auditLogQueryService, never()).search(any());
    }

    @Test
    @WithAuthUser(roles = "ROLE_AUDITOR", permissions = Permissions.AUDIT_LOG_VIEW)
    void userWithAuditView_canSearch_andFiltersAreBound() throws Exception {
        when(auditLogQueryService.search(any())).thenReturn(PageResponse.of(List.of(AuditLogResponseDto.builder()
                .id(11L)
                .eventTime(LocalDateTime.of(2026, 10, 4, 9, 30))
                .username("thungan")
                .action("PAYMENT_VOIDED")
                .resourceType("PAYMENT_TRANSACTION")
                .resourceId("21")
                .result("SUCCESS")
                .ip("203.0.113.7")
                .detail("{\"reason\":\"Thu nhầm\"}")
                .build()), 2, 50, 51));

        mockMvc.perform(get("/api/v1/audit-logs/search")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-04")
                        .param("username", "thu")
                        .param("action", "PAYMENT_VOIDED")
                        .param("resourceType", "PAYMENT_TRANSACTION")
                        .param("resourceId", "21")
                        .param("result", "SUCCESS")
                        .param("ip", "203.0.")
                        .param("keyword", "nhầm")
                        .param("userId", "7")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("PAYMENT_VOIDED"))
                .andExpect(jsonPath("$.data.content[0].resourceId").value("21"))
                .andExpect(jsonPath("$.data.totalRows").value(51))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        ArgumentCaptor<AuditLogFilterRequest> captor = ArgumentCaptor.forClass(AuditLogFilterRequest.class);
        verify(auditLogQueryService).search(captor.capture());
        AuditLogFilterRequest filter = captor.getValue();
        assertThat(filter.getFromDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(filter.getToDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(filter.getUsername()).isEqualTo("thu");
        assertThat(filter.getUserId()).isEqualTo(7L);
        assertThat(filter.getIp()).isEqualTo("203.0.");
        assertThat(filter.getKeyword()).isEqualTo("nhầm");
        assertThat(filter.resolvePage()).isEqualTo(2);
        assertThat(filter.resolveSize()).isEqualTo(50);
    }

    @Test
    @WithAuthUser
    void admin_canSearchWithDefaults() throws Exception {
        when(auditLogQueryService.search(any())).thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/audit-logs/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(20));
    }

    @Test
    @WithAuthUser
    void invertedDateRange_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/search")
                        .param("fromDate", "2026-10-05")
                        .param("toDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(auditLogQueryService, never()).search(any());
    }

    @Test
    @WithAuthUser
    void invalidFilters_areRejected() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/search").param("result", "OK"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/audit-logs/search").param("size", "500"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/audit-logs/search").param("action", "LOGIN'; DROP"))
                .andExpect(status().isBadRequest());
        verify(auditLogQueryService, never()).search(any());
    }

    @Test
    @WithAuthUser
    void auditLog_isReadOnly() throws Exception {
        mockMvc.perform(post("/api/v1/audit-logs/search"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 405));
    }
}
