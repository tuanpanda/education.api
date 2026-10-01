package com.education.base.service;

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

/**
 * Báo cáo (chỉ đọc): dashboard, tổng hợp tài chính, tuổi nợ, thu tiền theo lớp, sổ công nợ học sinh và xuất Excel.
 */
public interface ReportService {

    DashboardMetricsResponse getDashboardMetrics(DashboardFilterRequest filter);

    FinanceSummaryDto getFinanceSummary(FinanceReportFilterRequest filter);

    DebtAgingDto getDebtAging(FinanceReportFilterRequest filter);

    ClassCollectionReportDto getClassCollection(FinanceReportFilterRequest filter);

    StudentLedgerDto getStudentLedger(Long studentId);

    ExportFileDto exportFinanceSummary(FinanceReportFilterRequest filter);

    ExportFileDto exportDebtAging(FinanceReportFilterRequest filter);

    ExportFileDto exportClassCollection(FinanceReportFilterRequest filter);

    ExportFileDto exportTuitionFees(FeeListExportFilterRequest filter);

    ExportFileDto exportPaymentTransactions(TransactionListExportFilterRequest filter);
}
