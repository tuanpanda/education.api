package com.education.base.service;

import com.education.base.dto.response.ClassCollectionReportDto;
import com.education.base.dto.response.DebtAgingDto;
import com.education.base.dto.response.FeeExportRowDto;
import com.education.base.dto.response.FinanceSummaryDto;
import com.education.base.dto.response.TransactionExportRowDto;

import java.util.List;

/**
 * Sinh file Excel {@code .xlsx} (Apache POI) cho báo cáo tài chính và danh sách khoản phí / giao dịch.
 * <p>
 * Mỗi sheet: dòng 1 tiêu đề, dòng 2 điều kiện lọc, dòng {@link #HEADER_ROW_INDEX} + 1 tiêu đề cột, dữ liệu ngay sau.
 * Số tiền là ô số (định dạng {@code #,##0}), ngày là ô ngày.
 */
public interface FinanceExportService {

    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Chỉ số (0-based) của dòng tiêu đề cột trên mọi sheet. */
    int HEADER_ROW_INDEX = 3;

    String SHEET_SUMMARY = "TongHop";
    String SHEET_STATUS = "TheoTrangThai";
    String SHEET_MONTHLY = "TheoThang";
    String SHEET_AGING_STUDENTS = "TheoHocSinh";
    String SHEET_AGING_FEES = "ChiTietKhoanPhi";
    String SHEET_CLASS_COLLECTION = "TheoLop";
    String SHEET_FEES = "KhoanHocPhi";
    String SHEET_TRANSACTIONS = "GiaoDich";

    byte[] financeSummaryWorkbook(FinanceSummaryDto summary);

    byte[] debtAgingWorkbook(DebtAgingDto aging);

    byte[] classCollectionWorkbook(ClassCollectionReportDto report);

    byte[] tuitionFeesWorkbook(List<FeeExportRowDto> rows, String filterDescription);

    byte[] paymentTransactionsWorkbook(List<TransactionExportRowDto> rows, String filterDescription);
}
