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
import com.education.base.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReportRepository reportRepository;
    private final FinanceExportService financeExportService;

    /** Đồng hồ cho năm mặc định / tên file; ghi đè trong test. */
    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardMetricsResponse getDashboardMetrics(DashboardFilterRequest filter) {
        return reportRepository.getDashboardMetrics(filter);
    }

    @Override
    @Transactional(readOnly = true)
    public FinanceSummaryDto getFinanceSummary(FinanceReportFilterRequest filter) {
        FinanceReportFilterRequest criteria = orEmpty(filter);
        return reportRepository.getFinanceSummary(criteria.getFromDate(), criteria.getToDate());
    }

    @Override
    @Transactional(readOnly = true)
    public DebtAgingDto getDebtAging(FinanceReportFilterRequest filter) {
        return reportRepository.getDebtAging(orEmpty(filter).getAsOfDate());
    }

    @Override
    @Transactional(readOnly = true)
    public ClassCollectionReportDto getClassCollection(FinanceReportFilterRequest filter) {
        FinanceReportFilterRequest criteria = orEmpty(filter);
        int year = criteria.getYear() != null ? criteria.getYear() : LocalDate.now(clock).getYear();
        List<ClassCollectionDto> rows = reportRepository.getClassCollection(year, criteria.getMonth());
        return ClassCollectionReportDto.builder()
                .year(year)
                .month(criteria.getMonth())
                .rows(new ArrayList<>(rows))
                .total(totalOf(rows))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLedgerDto getStudentLedger(Long studentId) {
        StudentLedgerDto ledger = reportRepository.getStudentLedger(studentId);
        List<StudentLedgerEntryDto> entries = ledger.getEntries() == null ? List.of() : ledger.getEntries();
        BigDecimal debit = sum(entries, StudentLedgerEntryDto::getDebitAmount);
        BigDecimal credit = sum(entries, StudentLedgerEntryDto::getCreditAmount);
        BigDecimal refund = sum(entries.stream()
                        .filter(e -> DomainConstants.LEDGER_ENTRY_REFUND.equals(e.getEntryType()))
                        .toList(),
                StudentLedgerEntryDto::getDebitAmount);
        ledger.setTotalDebit(debit);
        ledger.setTotalRefund(refund);
        ledger.setTotalCredit(credit);
        ledger.setBalance(debit.subtract(credit));
        return ledger;
    }

    @Override
    @Transactional(readOnly = true)
    public ExportFileDto exportFinanceSummary(FinanceReportFilterRequest filter) {
        FinanceSummaryDto summary = getFinanceSummary(filter);
        String fileName = "tong_hop_tai_chinh_" + fileDate(summary.getFromDate()) + "_"
                + fileDate(summary.getToDate()) + ".xlsx";
        return xlsx(fileName, financeExportService.financeSummaryWorkbook(summary));
    }

    @Override
    @Transactional(readOnly = true)
    public ExportFileDto exportDebtAging(FinanceReportFilterRequest filter) {
        DebtAgingDto aging = getDebtAging(filter);
        return xlsx("tuoi_no_hoc_phi_" + fileDate(aging.getAsOfDate()) + ".xlsx",
                financeExportService.debtAgingWorkbook(aging));
    }

    @Override
    @Transactional(readOnly = true)
    public ExportFileDto exportClassCollection(FinanceReportFilterRequest filter) {
        ClassCollectionReportDto report = getClassCollection(filter);
        String period = report.getMonth() == null
                ? String.valueOf(report.getYear())
                : String.format("%d_%02d", report.getYear(), report.getMonth());
        return xlsx("thu_tien_theo_lop_" + period + ".xlsx", financeExportService.classCollectionWorkbook(report));
    }

    @Override
    @Transactional(readOnly = true)
    public ExportFileDto exportTuitionFees(FeeListExportFilterRequest filter) {
        FeeListExportFilterRequest criteria = filter == null ? new FeeListExportFilterRequest() : filter;
        List<FeeExportRowDto> rows = ensureExportable(
                reportRepository.findFeesForExport(criteria, DomainConstants.FINANCE_EXPORT_MAX_ROWS + 1));
        return xlsx("danh_sach_khoan_hoc_phi_" + LocalDate.now(clock).format(FILE_DATE) + ".xlsx",
                financeExportService.tuitionFeesWorkbook(rows, describe(criteria)));
    }

    @Override
    @Transactional(readOnly = true)
    public ExportFileDto exportPaymentTransactions(TransactionListExportFilterRequest filter) {
        TransactionListExportFilterRequest criteria =
                filter == null ? new TransactionListExportFilterRequest() : filter;
        List<TransactionExportRowDto> rows = ensureExportable(
                reportRepository.findTransactionsForExport(criteria, DomainConstants.FINANCE_EXPORT_MAX_ROWS + 1));
        return xlsx("danh_sach_giao_dich_" + LocalDate.now(clock).format(FILE_DATE) + ".xlsx",
                financeExportService.paymentTransactionsWorkbook(rows, describe(criteria)));
    }

    // ------------------------------------------------------------------------------------------ hỗ trợ

    /** Dòng tổng cộng của báo cáo thu tiền theo lớp; tỷ lệ thu tính lại trên tổng, không cộng tỷ lệ từng lớp. */
    static ClassCollectionDto totalOf(List<ClassCollectionDto> rows) {
        BigDecimal net = sum(rows, ClassCollectionDto::getNetAmount);
        BigDecimal collected = sum(rows, ClassCollectionDto::getCollectedAmount);
        long feeCount = rows.stream().mapToLong(r -> r.getFeeCount() == null ? 0 : r.getFeeCount()).sum();
        long studentCount = rows.stream().mapToLong(r -> r.getStudentCount() == null ? 0 : r.getStudentCount()).sum();
        return ClassCollectionDto.builder()
                .feeCount(feeCount)
                .studentCount(studentCount)
                .billedAmount(sum(rows, ClassCollectionDto::getBilledAmount))
                .discountAmount(sum(rows, ClassCollectionDto::getDiscountAmount))
                .netAmount(net)
                .collectedAmount(collected)
                .outstandingAmount(sum(rows, ClassCollectionDto::getOutstandingAmount))
                .collectionRate(net.signum() > 0
                        ? collected.multiply(HUNDRED).divide(net, 2, RoundingMode.HALF_UP)
                        : null)
                .build();
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> value) {
        BigDecimal total = BigDecimal.ZERO;
        for (T row : rows) {
            BigDecimal amount = value.apply(row);
            if (amount != null) {
                total = total.add(amount);
            }
        }
        return total;
    }

    private static <T> List<T> ensureExportable(List<T> rows) {
        if (rows.size() > DomainConstants.FINANCE_EXPORT_MAX_ROWS) {
            throw new OracleBusinessException("EXPORT_TOO_LARGE",
                    "Dữ liệu xuất vượt quá " + DomainConstants.FINANCE_EXPORT_MAX_ROWS
                            + " dòng, vui lòng thu hẹp điều kiện lọc.");
        }
        return rows;
    }

    private static ExportFileDto xlsx(String fileName, byte[] content) {
        return new ExportFileDto(fileName, FinanceExportService.XLSX_CONTENT_TYPE, content);
    }

    private static FinanceReportFilterRequest orEmpty(FinanceReportFilterRequest filter) {
        return filter == null ? new FinanceReportFilterRequest() : filter;
    }

    private String fileDate(LocalDate date) {
        return (date == null ? LocalDate.now(clock) : date).format(FILE_DATE);
    }

    static String describe(FeeListExportFilterRequest filter) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "Từ khóa", filter.getKeyword());
        addPart(parts, "Trạng thái", filter.getStatus());
        addPart(parts, "Học sinh ID", filter.getStudentId());
        addPart(parts, "Lớp ID", filter.getClassId());
        addPart(parts, "Hạn thu từ", formatDate(filter.getDueFromDate()));
        addPart(parts, "Hạn thu đến", formatDate(filter.getDueToDate()));
        if (Boolean.TRUE.equals(filter.getOverdueOnly())) {
            parts.add("Chỉ khoản quá hạn");
        }
        return parts.isEmpty() ? "Tất cả khoản học phí" : String.join("; ", parts);
    }

    static String describe(TransactionListExportFilterRequest filter) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "Từ ngày", formatDate(filter.getFromDate()));
        addPart(parts, "Đến ngày", formatDate(filter.getToDate()));
        addPart(parts, "Hình thức", filter.getPaymentMethod());
        addPart(parts, "Trạng thái", filter.getStatus());
        addPart(parts, "Loại giao dịch", filter.getTransactionType());
        addPart(parts, "Học sinh ID", filter.getStudentId());
        addPart(parts, "Lớp ID", filter.getClassId());
        addPart(parts, "Mã khoản phí", filter.getFeeCode());
        addPart(parts, "Số phiếu", filter.getReceiptNo());
        addPart(parts, "Từ khóa", filter.getKeyword());
        return parts.isEmpty() ? "Tất cả giao dịch" : String.join("; ", parts);
    }

    private static void addPart(List<String> parts, String label, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            parts.add(label + ": " + String.valueOf(value).trim());
        }
    }

    private static String formatDate(LocalDate date) {
        return date == null ? null : date.format(VN_DATE);
    }
}
