package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ReportService reportService;

    @Test
    void dashboard_returnsMetricsFromProcedure() throws Exception {
        when(reportService.getDashboardMetrics(any(DashboardFilterRequest.class)))
                .thenReturn(DashboardMetricsResponse.builder()
                        .fromDate(LocalDate.of(2026, 1, 1))
                        .toDate(LocalDate.of(2026, 9, 18))
                        .totalStudents(120L)
                        .activeStudents(100L)
                        .totalCollected(new BigDecimal("50000000"))
                        .build());

        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-09-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.totalStudents").value(120))
                .andExpect(jsonPath("$.data.activeStudents").value(100));
    }
}
