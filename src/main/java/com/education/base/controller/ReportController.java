package com.education.base.controller;

import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.common.ApiResponse;
import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.request.FeeListExportFilterRequest;
import com.education.base.dto.request.FinanceReportFilterRequest;
import com.education.base.dto.request.TransactionListExportFilterRequest;
import com.education.base.dto.response.ClassCollectionReportDto;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.dto.response.DebtAgingDto;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.FinanceSummaryDto;
import com.education.base.dto.response.StudentLedgerDto;
import com.education.base.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Validated
@Tag(name = "Báo cáo", description = "Dashboard, báo cáo tài chính (PRC_RPT_*) và xuất Excel - chỉ đọc dữ liệu")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Chỉ số dashboard",
            description = "Bỏ trống khoảng ngày thì procedure lấy 12 tháng gần nhất.")
    @GetMapping("/dashboard")
    @RequirePermission(Permissions.DASHBOARD_VIEW)
    public ApiResponse<DashboardMetricsResponse> dashboard(@Valid @ModelAttribute DashboardFilterRequest filter) {
        return ApiResponse.success(reportService.getDashboardMetrics(filter));
    }

    // ------------------------------------------------------------------------------------------ tài chính

    @Operation(summary = "Tổng hợp tài chính",
            description = "PRC_RPT_FINANCE_SUMMARY: đã lập, miễn giảm, thực thu, còn phải thu, quá hạn, số khoản theo "
                    + "trạng thái và chuỗi theo tháng. Tham số fromDate/toDate; bỏ trống là 12 tháng gần nhất.")
    @GetMapping("/finance/summary")
    @RequirePermission({Permissions.FINANCE_REPORT_VIEW, Permissions.FINANCE_DASHBOARD_VIEW})
    public ApiResponse<FinanceSummaryDto> financeSummary(@Valid @ModelAttribute FinanceReportFilterRequest filter) {
        return ApiResponse.success(reportService.getFinanceSummary(filter));
    }

    @Operation(summary = "Tuổi nợ học phí",
            description = "PRC_RPT_DEBT_AGING: còn phải thu theo nhóm chưa tới hạn / 1-30 / 31-60 / 61-90 / trên 90 ngày "
                    + "quá hạn, theo học sinh và theo khoản phí (gồm cả học sinh không còn ACTIVE). Tham số asOfDate.")
    @GetMapping("/finance/debt-aging")
    @RequirePermission(Permissions.FINANCE_REPORT_VIEW)
    public ApiResponse<DebtAgingDto> debtAging(@Valid @ModelAttribute FinanceReportFilterRequest filter) {
        return ApiResponse.success(reportService.getDebtAging(filter));
    }

    @Operation(summary = "Thu tiền theo lớp",
            description = "PRC_RPT_CLASS_COLLECTION: phải thu / miễn giảm / thực thu / còn phải thu / tỷ lệ thu theo "
                    + "lớp. Tham số year (mặc định năm hiện tại), month (bỏ trống là cả năm).")
    @GetMapping("/finance/class-collection")
    @RequirePermission(Permissions.FINANCE_REPORT_VIEW)
    public ApiResponse<ClassCollectionReportDto> classCollection(
            @Valid @ModelAttribute FinanceReportFilterRequest filter) {
        return ApiResponse.success(reportService.getClassCollection(filter));
    }

    @Operation(summary = "Sổ công nợ học sinh",
            description = "PRC_RPT_STUDENT_LEDGER: khoản phí (ghi nợ) và giao dịch thành công (ghi có) theo thời gian, "
                    + "kèm số dư lũy kế.")
    @GetMapping("/finance/student-ledger/{studentId}")
    @RequirePermission({Permissions.FINANCE_REPORT_VIEW, Permissions.TUITION_FEE_VIEW})
    public ApiResponse<StudentLedgerDto> studentLedger(@PathVariable("studentId") Long studentId) {
        return ApiResponse.success(reportService.getStudentLedger(studentId));
    }

    // ------------------------------------------------------------------------------------------ xuất Excel

    @Operation(summary = "Xuất Excel tổng hợp tài chính")
    @GetMapping("/finance/summary/export")
    @RequirePermission(Permissions.FINANCE_REPORT_EXPORT)
    public ResponseEntity<Resource> exportFinanceSummary(@Valid @ModelAttribute FinanceReportFilterRequest filter) {
        return download(reportService.exportFinanceSummary(filter));
    }

    @Operation(summary = "Xuất Excel tuổi nợ học phí")
    @GetMapping("/finance/debt-aging/export")
    @RequirePermission(Permissions.FINANCE_REPORT_EXPORT)
    public ResponseEntity<Resource> exportDebtAging(@Valid @ModelAttribute FinanceReportFilterRequest filter) {
        return download(reportService.exportDebtAging(filter));
    }

    @Operation(summary = "Xuất Excel thu tiền theo lớp")
    @GetMapping("/finance/class-collection/export")
    @RequirePermission(Permissions.FINANCE_REPORT_EXPORT)
    public ResponseEntity<Resource> exportClassCollection(@Valid @ModelAttribute FinanceReportFilterRequest filter) {
        return download(reportService.exportClassCollection(filter));
    }

    @Operation(summary = "Xuất Excel danh sách khoản học phí",
            description = "Cùng tham số lọc với /api/v1/tuition-fees/search (không phân trang); gồm cả học sinh không "
                    + "còn ACTIVE. Tối đa 20.000 dòng.")
    @GetMapping("/export/tuition-fees")
    @RequirePermission(Permissions.TUITION_FEE_EXPORT)
    public ResponseEntity<Resource> exportTuitionFees(@Valid @ModelAttribute FeeListExportFilterRequest filter) {
        return download(reportService.exportTuitionFees(filter));
    }

    @Operation(summary = "Xuất Excel danh sách giao dịch thanh toán",
            description = "Lọc theo khoảng ngày thanh toán, hình thức, trạng thái, học sinh, lớp, mã khoản phí, từ khóa. "
                    + "Tối đa 20.000 dòng.")
    @GetMapping("/export/payment-transactions")
    @RequirePermission(Permissions.PAYMENT_HISTORY_EXPORT)
    public ResponseEntity<Resource> exportPaymentTransactions(
            @Valid @ModelAttribute TransactionListExportFilterRequest filter) {
        return download(reportService.exportPaymentTransactions(filter));
    }

    private static ResponseEntity<Resource> download(ExportFileDto file) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .contentLength(file.content().length)
                .body(new ByteArrayResource(file.content()));
    }
}
