package com.education.base.controller;

import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.request.FeeListExportFilterRequest;
import com.education.base.dto.request.FinanceReportFilterRequest;
import com.education.base.dto.request.TransactionListExportFilterRequest;
import com.education.base.dto.response.ClassCollectionDto;
import com.education.base.dto.response.ClassCollectionReportDto;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.dto.response.DebtAgingDto;
import com.education.base.dto.response.DebtAgingFeeDto;
import com.education.base.dto.response.DebtAgingStudentDto;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.FeeExportRowDto;
import com.education.base.dto.response.FinanceMonthlyDto;
import com.education.base.dto.response.FinanceSummaryDto;
import com.education.base.dto.response.StudentLedgerDto;
import com.education.base.dto.response.StudentLedgerEntryDto;
import com.education.base.dto.response.TransactionExportRowDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.security.Permissions;
import com.education.base.service.FinanceExportService;
import com.education.base.service.ReportService;
import com.education.base.service.impl.FinanceExportServiceImpl;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class ReportControllerTest {

    private static final String XLSX = FinanceExportService.XLSX_CONTENT_TYPE;

    /** Sinh workbook thật để kiểm tra nội dung file trả về qua HTTP. */
    private final FinanceExportService excel = new FinanceExportServiceImpl();

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ReportService reportService;

    // ------------------------------------------------------------------------------------------ dashboard

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

    @Test
    void dashboard_exposesB7Fields() throws Exception {
        when(reportService.getDashboardMetrics(any(DashboardFilterRequest.class)))
                .thenReturn(DashboardMetricsResponse.builder()
                        .totalReceivable(new BigDecimal("3000000"))
                        .totalBilled(new BigDecimal("9000000"))
                        .overdueFees(4L)
                        .overdueAmount(new BigDecimal("1200000"))
                        .build());

        mockMvc.perform(get("/api/v1/reports/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReceivable").value(3000000))
                .andExpect(jsonPath("$.data.totalBilled").value(9000000))
                .andExpect(jsonPath("$.data.overdueFees").value(4))
                .andExpect(jsonPath("$.data.overdueAmount").value(1200000));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER")
    void dashboard_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    // ------------------------------------------------------------------------------------------ tổng hợp

    @Test
    void financeSummary_returnsSummaryAndPassesFilter() throws Exception {
        when(reportService.getFinanceSummary(any(FinanceReportFilterRequest.class))).thenReturn(summary());

        mockMvc.perform(get("/api/v1/reports/finance/summary")
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netBilled").value(9000000))
                .andExpect(jsonPath("$.data.totalCollected").value(6000000))
                .andExpect(jsonPath("$.data.monthly[0].month").value("2026-09"));

        ArgumentCaptor<FinanceReportFilterRequest> captor = ArgumentCaptor.forClass(FinanceReportFilterRequest.class);
        verify(reportService).getFinanceSummary(captor.capture());
        assertThat(captor.getValue().getFromDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(captor.getValue().getToDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_DASHBOARD_VIEW)
    void financeSummary_allowsFinanceDashboardViewer() throws Exception {
        when(reportService.getFinanceSummary(any(FinanceReportFilterRequest.class))).thenReturn(summary());

        mockMvc.perform(get("/api/v1/reports/finance/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMISSION", permissions = Permissions.DASHBOARD_VIEW)
    void financeSummary_withOnlyDashboardView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/finance/summary"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    @Test
    void financeSummary_procedureRangeError_returns400() throws Exception {
        when(reportService.getFinanceSummary(any(FinanceReportFilterRequest.class)))
                .thenThrow(new OracleBusinessException("INVALID_DATE_RANGE", "Từ ngày phải nhỏ hơn hoặc bằng đến ngày."));

        mockMvc.perform(get("/api/v1/reports/finance/summary")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    // ------------------------------------------------------------------------------------------ tuổi nợ

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_VIEW)
    void debtAging_returnsBuckets() throws Exception {
        when(reportService.getDebtAging(any(FinanceReportFilterRequest.class))).thenReturn(aging());

        mockMvc.perform(get("/api/v1/reports/finance/debt-aging").param("asOfDate", "2026-10-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.due31To60Amount").value(500000))
                .andExpect(jsonPath("$.data.students[0].studentStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.data.fees[0].agingBucket").value("D31_60"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.TUITION_FEE_VIEW)
    void debtAging_withoutReportView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/finance/debt-aging"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    // ------------------------------------------------------------------------------------------ theo lớp

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_VIEW)
    void classCollection_returnsRowsAndTotal() throws Exception {
        when(reportService.getClassCollection(any(FinanceReportFilterRequest.class))).thenReturn(classReport());

        mockMvc.perform(get("/api/v1/reports/finance/class-collection")
                        .param("year", "2026")
                        .param("month", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].classCode").value("L01"))
                .andExpect(jsonPath("$.data.total.collectionRate").value(75.0));
    }

    @Test
    void classCollection_invalidMonth_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/finance/class-collection").param("month", "13"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reportService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER")
    void classCollection_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/finance/class-collection"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------ sổ công nợ

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.TUITION_FEE_VIEW)
    void studentLedger_allowsTuitionFeeViewer() throws Exception {
        when(reportService.getStudentLedger(eq(7L))).thenReturn(StudentLedgerDto.builder()
                .studentId(7L)
                .studentCode("HS007")
                .balance(new BigDecimal("200000"))
                .entries(List.of(StudentLedgerEntryDto.builder()
                        .entryType("FEE")
                        .refCode("HP001")
                        .debitAmount(new BigDecimal("200000"))
                        .creditAmount(BigDecimal.ZERO)
                        .balance(new BigDecimal("200000"))
                        .build()))
                .build());

        mockMvc.perform(get("/api/v1/reports/finance/student-ledger/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCode").value("HS007"))
                .andExpect(jsonPath("$.data.entries[0].entryType").value("FEE"))
                .andExpect(jsonPath("$.data.balance").value(200000));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_VIEW)
    void studentLedger_allowsFinanceReportViewer() throws Exception {
        when(reportService.getStudentLedger(eq(7L))).thenReturn(StudentLedgerDto.builder().studentId(7L).build());

        mockMvc.perform(get("/api/v1/reports/finance/student-ledger/7"))
                .andExpect(status().isOk());
    }

    @Test
    void studentLedger_unknownStudent_returns404() throws Exception {
        when(reportService.getStudentLedger(eq(99L)))
                .thenThrow(new OracleBusinessException("STUDENT_NOT_FOUND", "Không tìm thấy học sinh ID: 99"));

        mockMvc.perform(get("/api/v1/reports/finance/student-ledger/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMISSION", permissions = Permissions.DASHBOARD_VIEW)
    void studentLedger_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/finance/student-ledger/7"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    // ------------------------------------------------------------------------------------------ xuất Excel

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_EXPORT)
    void exportFinanceSummary_returnsWorkbook() throws Exception {
        when(reportService.exportFinanceSummary(any(FinanceReportFilterRequest.class))).thenReturn(new ExportFileDto(
                "tong_hop_tai_chinh_20260101_20260930.xlsx", XLSX, excel.financeSummaryWorkbook(summary())));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/finance/summary/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("tong_hop_tai_chinh_20260101_20260930.xlsx")))
                .andReturn();

        assertWorkbook(result, FinanceExportService.SHEET_MONTHLY, "Tháng", "2026-09");
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_VIEW)
    void exportFinanceSummary_withOnlyView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/finance/summary/export"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_EXPORT)
    void exportDebtAging_returnsWorkbook() throws Exception {
        when(reportService.exportDebtAging(any(FinanceReportFilterRequest.class))).thenReturn(new ExportFileDto(
                "tuoi_no_hoc_phi_20261002.xlsx", XLSX, excel.debtAgingWorkbook(aging())));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/finance/debt-aging/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn();

        assertWorkbook(result, FinanceExportService.SHEET_AGING_FEES, "Mã khoản phí", "HP001");
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_VIEW)
    void exportDebtAging_withOnlyView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/finance/debt-aging/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_EXPORT)
    void exportClassCollection_returnsWorkbook() throws Exception {
        when(reportService.exportClassCollection(any(FinanceReportFilterRequest.class))).thenReturn(new ExportFileDto(
                "thu_tien_theo_lop_2026_09.xlsx", XLSX, excel.classCollectionWorkbook(classReport())));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/finance/class-collection/export")
                        .param("year", "2026").param("month", "9"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn();

        assertWorkbook(result, FinanceExportService.SHEET_CLASS_COLLECTION, "Mã lớp", "L01");
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.FINANCE_REPORT_VIEW)
    void exportClassCollection_withOnlyView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/finance/class-collection/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.TUITION_FEE_EXPORT)
    void exportTuitionFees_returnsWorkbookAndPassesFilters() throws Exception {
        List<FeeExportRowDto> rows = List.of(FeeExportRowDto.builder()
                .id(1L).feeCode("HP001").studentCode("HS001").studentName("Nguyễn Văn A")
                .totalAmount(new BigDecimal("1000000")).discountAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO).remainingAmount(new BigDecimal("1000000"))
                .dueDate(LocalDate.of(2026, 9, 30)).status("UNPAID").build());
        when(reportService.exportTuitionFees(any(FeeListExportFilterRequest.class))).thenReturn(new ExportFileDto(
                "danh_sach_khoan_hoc_phi_20261002.xlsx", XLSX, excel.tuitionFeesWorkbook(rows, "Tất cả")));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/export/tuition-fees")
                        .param("keyword", "HS001")
                        .param("status", "UNPAID")
                        .param("overdueOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn();

        assertWorkbook(result, FinanceExportService.SHEET_FEES, "Mã khoản phí", "HP001");
        ArgumentCaptor<FeeListExportFilterRequest> captor = ArgumentCaptor.forClass(FeeListExportFilterRequest.class);
        verify(reportService).exportTuitionFees(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("HS001");
        assertThat(captor.getValue().getStatus()).isEqualTo("UNPAID");
        assertThat(captor.getValue().getOverdueOnly()).isTrue();
    }

    @Test
    void exportTuitionFees_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/tuition-fees").param("status", "VOIDED"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reportService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.TUITION_FEE_VIEW)
    void exportTuitionFees_withOnlyView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/tuition-fees"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.PAYMENT_HISTORY_EXPORT)
    void exportPaymentTransactions_returnsWorkbook() throws Exception {
        List<TransactionExportRowDto> rows = List.of(TransactionExportRowDto.builder()
                .id(5L).transactionCode("GD0005").feeCode("HP001").studentCode("HS001")
                .amount(new BigDecimal("500000")).paymentMethod("CASH").status("SUCCESS")
                .paymentDate(LocalDateTime.of(2026, 9, 15, 9, 30)).build());
        when(reportService.exportPaymentTransactions(any(TransactionListExportFilterRequest.class)))
                .thenReturn(new ExportFileDto("danh_sach_giao_dich_20261002.xlsx", XLSX,
                        excel.paymentTransactionsWorkbook(rows, "Tất cả")));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/export/payment-transactions")
                        .param("fromDate", "2026-09-01")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn();

        assertWorkbook(result, FinanceExportService.SHEET_TRANSACTIONS, "Mã giao dịch", "GD0005");
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = Permissions.PAYMENT_HISTORY_VIEW)
    void exportPaymentTransactions_withOnlyView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/payment-transactions"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    // ------------------------------------------------------------------------------------------ dữ liệu mẫu

    private static void assertWorkbook(MvcResult result, String sheetName, String firstHeader, String firstValue)
            throws IOException {
        byte[] body = result.getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = workbook.getSheet(sheetName);
            assertThat(sheet).as("sheet %s", sheetName).isNotNull();
            Row header = sheet.getRow(FinanceExportService.HEADER_ROW_INDEX);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo(firstHeader);
            Row first = sheet.getRow(FinanceExportService.HEADER_ROW_INDEX + 1);
            assertThat(first).as("dòng dữ liệu đầu tiên").isNotNull();
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo(firstValue);
        }
    }

    private static FinanceSummaryDto summary() {
        return FinanceSummaryDto.builder()
                .fromDate(LocalDate.of(2026, 1, 1))
                .toDate(LocalDate.of(2026, 9, 30))
                .totalBilled(new BigDecimal("10000000"))
                .totalDiscount(new BigDecimal("1000000"))
                .netBilled(new BigDecimal("9000000"))
                .totalCollected(new BigDecimal("6000000"))
                .totalOutstanding(new BigDecimal("3000000"))
                .monthly(List.of(FinanceMonthlyDto.builder()
                        .month("2026-09")
                        .billedAmount(new BigDecimal("9000000"))
                        .collectedAmount(new BigDecimal("6000000"))
                        .feeCount(9L)
                        .transactionCount(6L)
                        .build()))
                .build();
    }

    private static DebtAgingDto aging() {
        return DebtAgingDto.builder()
                .asOfDate(LocalDate.of(2026, 10, 2))
                .due31To60Amount(new BigDecimal("500000"))
                .totalOutstanding(new BigDecimal("500000"))
                .feeCount(1L)
                .studentCount(1L)
                .students(List.of(DebtAgingStudentDto.builder()
                        .studentId(3L).studentCode("HS003").studentName("Trần Thị B").studentStatus("INACTIVE")
                        .due31To60Amount(new BigDecimal("500000")).totalOutstanding(new BigDecimal("500000"))
                        .feeCount(1L).maxDaysPastDue(45).build()))
                .fees(List.of(DebtAgingFeeDto.builder()
                        .feeId(1L).feeCode("HP001").studentCode("HS003").studentName("Trần Thị B")
                        .studentStatus("INACTIVE").dueDate(LocalDate.of(2026, 8, 18))
                        .remainingAmount(new BigDecimal("500000")).daysPastDue(45).agingBucket("D31_60").build()))
                .build();
    }

    private static ClassCollectionReportDto classReport() {
        ClassCollectionDto row = ClassCollectionDto.builder()
                .classId(1L).classCode("L01").className("Toán 6")
                .feeCount(4L).studentCount(4L)
                .billedAmount(new BigDecimal("4000000")).discountAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("4000000")).collectedAmount(new BigDecimal("3000000"))
                .outstandingAmount(new BigDecimal("1000000")).collectionRate(new BigDecimal("75.00"))
                .build();
        ClassCollectionDto total = ClassCollectionDto.builder()
                .feeCount(4L).studentCount(4L)
                .billedAmount(new BigDecimal("4000000")).discountAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("4000000")).collectedAmount(new BigDecimal("3000000"))
                .outstandingAmount(new BigDecimal("1000000")).collectionRate(new BigDecimal("75.00"))
                .build();
        return ClassCollectionReportDto.builder().year(2026).month(9).rows(List.of(row)).total(total).build();
    }
}
