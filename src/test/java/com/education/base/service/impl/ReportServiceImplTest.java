package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.request.FeeListExportFilterRequest;
import com.education.base.dto.request.FinanceReportFilterRequest;
import com.education.base.dto.request.TransactionListExportFilterRequest;
import com.education.base.dto.response.ClassCollectionDto;
import com.education.base.dto.response.ClassCollectionReportDto;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.dto.response.DebtAgingDto;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.FeeExportRowDto;
import com.education.base.dto.response.FinanceSummaryDto;
import com.education.base.dto.response.StudentLedgerDto;
import com.education.base.dto.response.StudentLedgerEntryDto;
import com.education.base.dto.response.TransactionExportRowDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ReportRepository;
import com.education.base.service.FinanceExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private FinanceExportService financeExportService;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(reportRepository, financeExportService);
        service.setClock(Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), ZONE));
    }

    @Test
    void getDashboardMetrics_passesThroughB7Fields() {
        DashboardMetricsResponse metrics = DashboardMetricsResponse.builder()
                .totalReceivable(new BigDecimal("3000000"))
                .totalBilled(new BigDecimal("9000000"))
                .overdueFees(4L)
                .overdueAmount(new BigDecimal("1200000"))
                .build();
        DashboardFilterRequest filter = new DashboardFilterRequest();
        when(reportRepository.getDashboardMetrics(filter)).thenReturn(metrics);

        DashboardMetricsResponse result = service.getDashboardMetrics(filter);

        assertThat(result.getTotalReceivable()).isEqualByComparingTo("3000000");
        assertThat(result.getTotalBilled()).isEqualByComparingTo("9000000");
        assertThat(result.getOverdueAmount()).isEqualByComparingTo("1200000");
    }

    @Test
    void getFinanceSummary_passesDateRange() {
        FinanceSummaryDto summary = FinanceSummaryDto.builder().netBilled(BigDecimal.TEN).build();
        when(reportRepository.getFinanceSummary(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(summary);

        FinanceSummaryDto result = service.getFinanceSummary(FinanceReportFilterRequest.builder()
                .fromDate(LocalDate.of(2026, 1, 1)).toDate(LocalDate.of(2026, 3, 31)).build());

        assertThat(result).isSameAs(summary);
    }

    @Test
    void getFinanceSummary_nullFilterUsesProcedureDefaults() {
        when(reportRepository.getFinanceSummary(null, null)).thenReturn(new FinanceSummaryDto());

        assertThat(service.getFinanceSummary(null)).isNotNull();
    }

    @Test
    void getDebtAging_passesAsOfDate() {
        DebtAgingDto aging = DebtAgingDto.builder().asOfDate(LocalDate.of(2026, 9, 30)).build();
        when(reportRepository.getDebtAging(LocalDate.of(2026, 9, 30))).thenReturn(aging);

        assertThat(service.getDebtAging(FinanceReportFilterRequest.builder()
                .asOfDate(LocalDate.of(2026, 9, 30)).build())).isSameAs(aging);
    }

    @Test
    void getClassCollection_defaultsToCurrentYearAndAddsTotal() {
        when(reportRepository.getClassCollection(2026, null)).thenReturn(List.of(
                classRow("L01", "2000000", "1500000", "500000"),
                classRow("L02", "1000000", "250000", "750000")));

        ClassCollectionReportDto report = service.getClassCollection(new FinanceReportFilterRequest());

        assertThat(report.getYear()).isEqualTo(2026);
        assertThat(report.getMonth()).isNull();
        assertThat(report.getRows()).hasSize(2);
        ClassCollectionDto total = report.getTotal();
        assertThat(total.getClassId()).isNull();
        assertThat(total.getFeeCount()).isEqualTo(4L);
        assertThat(total.getNetAmount()).isEqualByComparingTo("3000000");
        assertThat(total.getCollectedAmount()).isEqualByComparingTo("1750000");
        assertThat(total.getOutstandingAmount()).isEqualByComparingTo("1250000");
        // 1.750.000 / 3.000.000 = 58,33% - tính lại trên tổng, không cộng tỷ lệ từng lớp (75% + 25%).
        assertThat(total.getCollectionRate()).isEqualByComparingTo("58.33");
    }

    @Test
    void totalOf_emptyRowsHasNoRate() {
        ClassCollectionDto total = ReportServiceImpl.totalOf(List.of());

        assertThat(total.getNetAmount()).isEqualByComparingTo("0");
        assertThat(total.getCollectionRate()).isNull();
    }

    @Test
    void getStudentLedger_computesTotalsAndBalance() {
        StudentLedgerDto ledger = StudentLedgerDto.builder()
                .studentId(7L)
                .entries(new ArrayList<>(List.of(
                        entry(DomainConstants.LEDGER_ENTRY_FEE, "1000000", "0", "1000000"),
                        entry(DomainConstants.LEDGER_ENTRY_PAYMENT, "0", "400000", "600000"),
                        entry(DomainConstants.LEDGER_ENTRY_FEE, "500000", "0", "1100000"))))
                .build();
        when(reportRepository.getStudentLedger(7L)).thenReturn(ledger);

        StudentLedgerDto result = service.getStudentLedger(7L);

        assertThat(result.getTotalDebit()).isEqualByComparingTo("1500000");
        assertThat(result.getTotalCredit()).isEqualByComparingTo("400000");
        assertThat(result.getBalance()).isEqualByComparingTo("1100000");
    }

    @Test
    void getStudentLedger_noEntriesHasZeroBalance() {
        when(reportRepository.getStudentLedger(8L)).thenReturn(StudentLedgerDto.builder().studentId(8L).build());

        assertThat(service.getStudentLedger(8L).getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void exportFinanceSummary_namesFileByAppliedRange() {
        FinanceSummaryDto summary = FinanceSummaryDto.builder()
                .fromDate(LocalDate.of(2025, 11, 1)).toDate(LocalDate.of(2026, 10, 2)).build();
        when(reportRepository.getFinanceSummary(null, null)).thenReturn(summary);
        when(financeExportService.financeSummaryWorkbook(summary)).thenReturn(new byte[]{1, 2});

        ExportFileDto file = service.exportFinanceSummary(null);

        assertThat(file.fileName()).isEqualTo("tong_hop_tai_chinh_20251101_20261002.xlsx");
        assertThat(file.contentType()).isEqualTo(FinanceExportService.XLSX_CONTENT_TYPE);
        assertThat(file.content()).containsExactly(1, 2);
    }

    @Test
    void exportDebtAging_namesFileByAsOfDate() {
        DebtAgingDto aging = DebtAgingDto.builder().asOfDate(LocalDate.of(2026, 9, 30)).build();
        when(reportRepository.getDebtAging(any())).thenReturn(aging);
        when(financeExportService.debtAgingWorkbook(aging)).thenReturn(new byte[]{3});

        assertThat(service.exportDebtAging(new FinanceReportFilterRequest()).fileName())
                .isEqualTo("tuoi_no_hoc_phi_20260930.xlsx");
    }

    @Test
    void exportClassCollection_namesFileByPeriod() {
        when(reportRepository.getClassCollection(2026, 9)).thenReturn(List.of());
        when(financeExportService.classCollectionWorkbook(any(ClassCollectionReportDto.class)))
                .thenReturn(new byte[]{4});

        ExportFileDto file = service.exportClassCollection(FinanceReportFilterRequest.builder()
                .year(2026).month(9).build());

        assertThat(file.fileName()).isEqualTo("thu_tien_theo_lop_2026_09.xlsx");
    }

    @Test
    void exportTuitionFees_readsOneRowMoreThanLimitAndDescribesFilter() {
        FeeListExportFilterRequest filter = FeeListExportFilterRequest.builder()
                .status("UNPAID").overdueOnly(true).dueFromDate(LocalDate.of(2026, 9, 1)).build();
        List<FeeExportRowDto> rows = List.of(FeeExportRowDto.builder().feeCode("HP001").build());
        when(reportRepository.findFeesForExport(filter, DomainConstants.FINANCE_EXPORT_MAX_ROWS + 1)).thenReturn(rows);
        when(financeExportService.tuitionFeesWorkbook(eq(rows), anyString())).thenReturn(new byte[]{5});

        ExportFileDto file = service.exportTuitionFees(filter);

        assertThat(file.fileName()).isEqualTo("danh_sach_khoan_hoc_phi_20261002.xlsx");
        verify(financeExportService).tuitionFeesWorkbook(rows,
                "Trạng thái: UNPAID; Hạn thu từ: 01/09/2026; Chỉ khoản quá hạn");
    }

    @Test
    void exportTuitionFees_tooManyRows_isRejected() {
        List<FeeExportRowDto> rows = Collections.nCopies(DomainConstants.FINANCE_EXPORT_MAX_ROWS + 1,
                new FeeExportRowDto());
        when(reportRepository.findFeesForExport(any(), anyInt())).thenReturn(rows);

        assertThatThrownBy(() -> service.exportTuitionFees(null))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("EXPORT_TOO_LARGE");
        verify(financeExportService, never()).tuitionFeesWorkbook(anyList(), any());
    }

    @Test
    void exportPaymentTransactions_noFilterDescribesAll() {
        List<TransactionExportRowDto> rows = List.of(TransactionExportRowDto.builder().transactionCode("GD1").build());
        when(reportRepository.findTransactionsForExport(any(TransactionListExportFilterRequest.class), anyInt()))
                .thenReturn(rows);
        when(financeExportService.paymentTransactionsWorkbook(rows, "Tất cả giao dịch")).thenReturn(new byte[]{6});

        ExportFileDto file = service.exportPaymentTransactions(null);

        assertThat(file.fileName()).isEqualTo("danh_sach_giao_dich_20261002.xlsx");
        assertThat(file.content()).containsExactly(6);
    }

    @Test
    void describeTransactionFilter_listsGivenCriteria() {
        String text = ReportServiceImpl.describe(TransactionListExportFilterRequest.builder()
                .fromDate(LocalDate.of(2026, 9, 1)).toDate(LocalDate.of(2026, 9, 30))
                .paymentMethod("CASH").feeCode(" HP ").build());

        assertThat(text).isEqualTo("Từ ngày: 01/09/2026; Đến ngày: 30/09/2026; Hình thức: CASH; Mã khoản phí: HP");
    }

    @Test
    void exportDebtAging_withoutAsOfDateFallsBackToToday() {
        when(reportRepository.getDebtAging(isNull())).thenReturn(new DebtAgingDto());
        when(financeExportService.debtAgingWorkbook(any())).thenReturn(new byte[0]);

        assertThat(service.exportDebtAging(null).fileName()).isEqualTo("tuoi_no_hoc_phi_20261002.xlsx");
    }

    private static ClassCollectionDto classRow(String code, String net, String collected, String outstanding) {
        return ClassCollectionDto.builder()
                .classCode(code).feeCount(2L).studentCount(2L)
                .billedAmount(new BigDecimal(net)).discountAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal(net)).collectedAmount(new BigDecimal(collected))
                .outstandingAmount(new BigDecimal(outstanding))
                .build();
    }

    private static StudentLedgerEntryDto entry(String type, String debit, String credit, String balance) {
        return StudentLedgerEntryDto.builder()
                .entryType(type)
                .debitAmount(new BigDecimal(debit))
                .creditAmount(new BigDecimal(credit))
                .balance(new BigDecimal(balance))
                .build();
    }
}
