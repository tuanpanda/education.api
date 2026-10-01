package com.education.base.repository;

import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.request.FeeListExportFilterRequest;
import com.education.base.dto.request.TransactionListExportFilterRequest;
import com.education.base.dto.response.ClassCollectionDto;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.dto.response.DebtAgingDto;
import com.education.base.dto.response.FeeExportRowDto;
import com.education.base.dto.response.FinanceSummaryDto;
import com.education.base.dto.response.StudentLedgerDto;
import com.education.base.dto.response.TransactionExportRowDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo tổng hợp qua Standalone Procedure {@code PRC_RPT_*} (V1, V14_3) và truy vấn chỉ đọc cho file xuất Excel.
 * Không kế thừa {@code JpaRepository} vì không ánh xạ một bảng cụ thể. Mọi phương thức chỉ SELECT.
 */
public interface ReportRepository {

    /**
     * Lấy chỉ số bảng điều khiển trong khoảng thời gian lọc.
     * Bỏ trống ngày thì procedure tự lấy 12 tháng gần nhất.
     *
     * @param filter khoảng ngày; {@code null} tương đương không lọc.
     * @return chỉ số tổng hợp kèm doanh thu theo tháng.
     */
    DashboardMetricsResponse getDashboardMetrics(DashboardFilterRequest filter);

    /**
     * Tổng hợp tài chính ({@code PRC_RPT_FINANCE_SUMMARY}); ngày {@code null} thì procedure lấy 12 tháng gần nhất.
     */
    FinanceSummaryDto getFinanceSummary(LocalDate fromDate, LocalDate toDate);

    /** Tuổi nợ tại ngày chốt ({@code PRC_RPT_DEBT_AGING}); {@code null} là hôm nay. */
    DebtAgingDto getDebtAging(LocalDate asOfDate);

    /** Thu tiền theo lớp ({@code PRC_RPT_CLASS_COLLECTION}); {@code month = null} là cả năm. */
    List<ClassCollectionDto> getClassCollection(Integer year, Integer month);

    /** Sổ công nợ học sinh ({@code PRC_RPT_STUDENT_LEDGER}). */
    StudentLedgerDto getStudentLedger(Long studentId);

    /**
     * Danh sách khoản học phí cho file Excel (truy vấn chỉ đọc, không qua Service của Stream A).
     *
     * @param limit số dòng tối đa đọc về.
     */
    List<FeeExportRowDto> findFeesForExport(FeeListExportFilterRequest filter, int limit);

    /**
     * Danh sách giao dịch thanh toán cho file Excel (truy vấn chỉ đọc, không qua Service của Stream B).
     *
     * @param limit số dòng tối đa đọc về.
     */
    List<TransactionExportRowDto> findTransactionsForExport(TransactionListExportFilterRequest filter, int limit);
}
