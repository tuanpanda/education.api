package com.education.base.service.impl;

import com.education.base.dto.response.ClassCollectionDto;
import com.education.base.dto.response.ClassCollectionReportDto;
import com.education.base.dto.response.DebtAgingDto;
import com.education.base.dto.response.DebtAgingFeeDto;
import com.education.base.dto.response.DebtAgingStudentDto;
import com.education.base.dto.response.FeeExportRowDto;
import com.education.base.dto.response.FeeStatusSummaryDto;
import com.education.base.dto.response.FinanceMonthlyDto;
import com.education.base.dto.response.FinanceSummaryDto;
import com.education.base.dto.response.TransactionExportRowDto;
import com.education.base.service.FinanceExportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mở workbook sinh ra bằng POI và kiểm tra sheet, tiêu đề cột, dòng dữ liệu.
 */
class FinanceExportServiceImplTest {

    private static final int HEADER = FinanceExportService.HEADER_ROW_INDEX;

    private final FinanceExportServiceImpl service = new FinanceExportServiceImpl();

    @Test
    void financeSummaryWorkbook_hasSummaryStatusAndMonthlySheets() throws IOException {
        FinanceSummaryDto summary = FinanceSummaryDto.builder()
                .fromDate(LocalDate.of(2026, 1, 1))
                .toDate(LocalDate.of(2026, 9, 30))
                .totalBilled(new BigDecimal("10000000"))
                .totalDiscount(new BigDecimal("1000000"))
                .netBilled(new BigDecimal("9000000"))
                .totalCollected(new BigDecimal("6000000"))
                .totalRefunded(new BigDecimal("200000"))
                .transactionCount(6L)
                .totalOutstanding(new BigDecimal("3000000"))
                .overdueAmount(new BigDecimal("1000000"))
                .overdueFees(2L)
                .feeCount(9L)
                .statusBreakdown(List.of(FeeStatusSummaryDto.builder()
                        .status("OVERDUE").feeCount(2L)
                        .netAmount(new BigDecimal("2000000")).remainingAmount(new BigDecimal("1000000")).build()))
                .monthly(List.of(
                        FinanceMonthlyDto.builder().month("2026-08").billedAmount(BigDecimal.ZERO)
                                .collectedAmount(BigDecimal.ZERO).feeCount(0L).transactionCount(0L).build(),
                        FinanceMonthlyDto.builder().month("2026-09").billedAmount(new BigDecimal("9000000"))
                                .collectedAmount(new BigDecimal("6000000")).refundedAmount(new BigDecimal("200000"))
                                .feeCount(9L).transactionCount(6L).build()))
                .build();

        try (XSSFWorkbook workbook = open(service.financeSummaryWorkbook(summary))) {
            assertThat(sheetNames(workbook)).containsExactly(
                    FinanceExportService.SHEET_SUMMARY, FinanceExportService.SHEET_STATUS,
                    FinanceExportService.SHEET_MONTHLY);

            Sheet sheet = workbook.getSheet(FinanceExportService.SHEET_SUMMARY);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Tổng hợp tài chính");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("Từ ngày 01/01/2026 đến ngày 30/09/2026");
            assertThat(headers(sheet)).containsExactly("Chỉ tiêu", "Giá trị");
            Row first = sheet.getRow(HEADER + 1);
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo("Tổng tiền đã lập");
            assertThat(first.getCell(1).getNumericCellValue()).isEqualTo(10_000_000d);
            assertThat(first.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0");
            Row collected = sheet.getRow(HEADER + 4);
            assertThat(collected.getCell(0).getStringCellValue()).isEqualTo("Thực thu (sau hoàn tiền)");
            Row refunded = sheet.getRow(HEADER + 5);
            assertThat(refunded.getCell(0).getStringCellValue()).isEqualTo("Đã hoàn tiền");
            assertThat(refunded.getCell(1).getNumericCellValue()).isEqualTo(200_000d);

            Sheet status = workbook.getSheet(FinanceExportService.SHEET_STATUS);
            assertThat(headers(status)).containsExactly("Trạng thái", "Số khoản", "Phải thu sau miễn giảm", "Còn phải thu");
            assertThat(status.getRow(HEADER + 1).getCell(0).getStringCellValue()).isEqualTo("Quá hạn");

            Sheet monthly = workbook.getSheet(FinanceExportService.SHEET_MONTHLY);
            assertThat(headers(monthly)).containsExactly(
                    "Tháng", "Phải thu (theo kỳ)", "Số khoản", "Thực thu", "Đã hoàn", "Số giao dịch thu");
            assertThat(monthly.getLastRowNum()).isEqualTo(HEADER + 2);
            assertThat(monthly.getRow(HEADER + 2).getCell(3).getNumericCellValue()).isEqualTo(6_000_000d);
            assertThat(monthly.getRow(HEADER + 2).getCell(4).getNumericCellValue()).isEqualTo(200_000d);
        }
    }

    @Test
    void debtAgingWorkbook_listsBucketsStudentsAndFees() throws IOException {
        DebtAgingDto aging = DebtAgingDto.builder()
                .asOfDate(LocalDate.of(2026, 10, 2))
                .notDueAmount(new BigDecimal("100000"))
                .due0To30Amount(BigDecimal.ZERO).due31To60Amount(new BigDecimal("500000"))
                .due61To90Amount(BigDecimal.ZERO).dueOver90Amount(BigDecimal.ZERO)
                .totalOutstanding(new BigDecimal("600000")).feeCount(2L).studentCount(1L)
                .students(List.of(DebtAgingStudentDto.builder()
                        .studentCode("HS003").studentName("Trần Thị B").studentStatus("INACTIVE")
                        .due31To60Amount(new BigDecimal("500000")).totalOutstanding(new BigDecimal("600000"))
                        .feeCount(2L).oldestDueDate(LocalDate.of(2026, 8, 18)).maxDaysPastDue(45).build()))
                .fees(List.of(DebtAgingFeeDto.builder()
                        .feeCode("HP001").studentCode("HS003").studentName("Trần Thị B").studentStatus("DELETED")
                        .classCode("L01").className("Toán 6").feeYear(2026).feeMonth(8)
                        .dueDate(LocalDate.of(2026, 8, 18)).status("OVERDUE")
                        .netAmount(new BigDecimal("500000")).paidAmount(BigDecimal.ZERO)
                        .remainingAmount(new BigDecimal("500000")).daysPastDue(45).agingBucket("D31_60").build()))
                .build();

        try (XSSFWorkbook workbook = open(service.debtAgingWorkbook(aging))) {
            assertThat(sheetNames(workbook)).containsExactly(
                    FinanceExportService.SHEET_SUMMARY, FinanceExportService.SHEET_AGING_STUDENTS,
                    FinanceExportService.SHEET_AGING_FEES);

            Sheet summary = workbook.getSheet(FinanceExportService.SHEET_SUMMARY);
            assertThat(summary.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Ngày chốt: 02/10/2026");
            assertThat(summary.getRow(HEADER + 2).getCell(0).getStringCellValue()).isEqualTo("Quá hạn 1-30 ngày");
            assertThat(summary.getRow(HEADER + 3).getCell(1).getNumericCellValue()).isEqualTo(500_000d);

            Sheet students = workbook.getSheet(FinanceExportService.SHEET_AGING_STUDENTS);
            assertThat(headers(students)).startsWith("Mã học sinh", "Họ và tên", "Trạng thái HS", "Chưa tới hạn");
            Row student = students.getRow(HEADER + 1);
            assertThat(student.getCell(0).getStringCellValue()).isEqualTo("HS003");
            assertThat(student.getCell(2).getStringCellValue()).isEqualTo("Ngừng học");
            Cell oldest = student.getCell(10);
            assertThat(DateUtil.isCellDateFormatted(oldest)).isTrue();
            assertThat(oldest.getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 18));

            Sheet fees = workbook.getSheet(FinanceExportService.SHEET_AGING_FEES);
            Row fee = fees.getRow(HEADER + 1);
            assertThat(fee.getCell(0).getStringCellValue()).isEqualTo("HP001");
            assertThat(fee.getCell(3).getStringCellValue()).isEqualTo("Đã xóa");
            assertThat(fee.getCell(4).getStringCellValue()).isEqualTo("L01 - Toán 6");
            assertThat(fee.getCell(5).getStringCellValue()).isEqualTo("08/2026");
            assertThat(fee.getCell(12).getStringCellValue()).isEqualTo("Quá hạn 31-60 ngày");
        }
    }

    @Test
    void classCollectionWorkbook_appendsTotalRowAndLabelsFeesWithoutClass() throws IOException {
        ClassCollectionDto withClass = ClassCollectionDto.builder()
                .classId(1L).classCode("L01").className("Toán 6").feeCount(2L).studentCount(2L)
                .billedAmount(new BigDecimal("2000000")).discountAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("2000000")).collectedAmount(new BigDecimal("1500000"))
                .outstandingAmount(new BigDecimal("500000")).collectionRate(new BigDecimal("75.00")).build();
        ClassCollectionDto noClass = ClassCollectionDto.builder()
                .feeCount(1L).studentCount(1L)
                .billedAmount(new BigDecimal("1000000")).discountAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("1000000")).collectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("1000000")).collectionRate(BigDecimal.ZERO).build();
        ClassCollectionDto total = ReportServiceImpl.totalOf(List.of(withClass, noClass));
        ClassCollectionReportDto report = ClassCollectionReportDto.builder()
                .year(2026).month(9).rows(List.of(withClass, noClass)).total(total).build();

        try (XSSFWorkbook workbook = open(service.classCollectionWorkbook(report))) {
            Sheet sheet = workbook.getSheet(FinanceExportService.SHEET_CLASS_COLLECTION);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Tháng 09/2026");
            assertThat(headers(sheet)).containsExactly("Mã lớp", "Tên lớp", "Số khoản", "Số học sinh",
                    "Tổng tiền đã lập", "Miễn giảm", "Phải thu", "Thực thu", "Còn phải thu", "Tỷ lệ thu (%)");
            assertThat(sheet.getRow(HEADER + 1).getCell(0).getStringCellValue()).isEqualTo("L01");
            assertThat(sheet.getRow(HEADER + 1).getCell(9).getNumericCellValue()).isEqualTo(75d);
            assertThat(sheet.getRow(HEADER + 2).getCell(1).getStringCellValue()).isEqualTo("(Không gắn lớp)");

            Row totalRow = sheet.getRow(HEADER + 3);
            assertThat(totalRow.getCell(0).getStringCellValue()).isEqualTo("TỔNG CỘNG");
            assertThat(totalRow.getCell(6).getNumericCellValue()).isEqualTo(3_000_000d);
            assertThat(totalRow.getCell(9).getNumericCellValue()).isEqualTo(50d);
        }
    }

    @Test
    void tuitionFeesWorkbook_writesTypedCells() throws IOException {
        FeeExportRowDto row = FeeExportRowDto.builder()
                .id(1L).feeCode("HP001").studentCode("HS001").studentName("Nguyễn Văn A").studentStatus("ACTIVE")
                .classCode("L01").className("Toán 6").feeYear(2026).feeMonth(9)
                .totalAmount(new BigDecimal("1200000")).discountAmount(new BigDecimal("200000"))
                .paidAmount(new BigDecimal("300000")).remainingAmount(new BigDecimal("700000"))
                .dueDate(LocalDate.of(2026, 9, 30)).status("PARTIAL").note("Ghi chú")
                .createdAt(LocalDateTime.of(2026, 9, 1, 8, 0)).build();

        try (XSSFWorkbook workbook = open(service.tuitionFeesWorkbook(List.of(row), "Trạng thái: PARTIAL"))) {
            Sheet sheet = workbook.getSheet(FinanceExportService.SHEET_FEES);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Trạng thái: PARTIAL");
            assertThat(headers(sheet)).containsExactly("Mã khoản phí", "Mã học sinh", "Họ và tên", "Trạng thái HS",
                    "Mã lớp", "Tên lớp", "Kỳ thu", "Tổng tiền", "Miễn giảm", "Đã thu", "Còn phải thu", "Hạn thu",
                    "Trạng thái", "Ghi chú", "Ngày lập");
            Row data = sheet.getRow(HEADER + 1);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("HP001");
            assertThat(data.getCell(3).getStringCellValue()).isEqualTo("Đang học");
            assertThat(data.getCell(6).getStringCellValue()).isEqualTo("09/2026");
            assertThat(data.getCell(10).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(data.getCell(10).getNumericCellValue()).isEqualTo(700_000d);
            assertThat(data.getCell(11).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(data.getCell(12).getStringCellValue()).isEqualTo("Thu một phần");
            assertThat(data.getCell(14).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 0));
        }
    }

    @Test
    void paymentTransactionsWorkbook_writesHeadersAndRows() throws IOException {
        TransactionExportRowDto row = TransactionExportRowDto.builder()
                .id(5L).transactionCode("GD0005").feeCode("HP001").studentCode("HS001").studentName("Nguyễn Văn A")
                .amount(new BigDecimal("500000")).paymentMethod("VIETQR").status("SUCCESS")
                .paymentDate(LocalDateTime.of(2026, 9, 15, 9, 30)).createdBy("accountant1")
                .receiptNo("PT2609-0005").transactionType("PAYMENT").payerName("Phụ huynh A").build();
        TransactionExportRowDto refund = TransactionExportRowDto.builder()
                .id(6L).transactionCode("GD0006").feeCode("HP001").amount(new BigDecimal("200000"))
                .paymentMethod("CASH").status("SUCCESS").transactionType("REFUND").receiptNo("PC2609-0001")
                .refTransactionCode("GD0005").paymentDate(LocalDateTime.of(2026, 9, 16, 9, 0)).build();
        TransactionExportRowDto voided = TransactionExportRowDto.builder()
                .id(7L).transactionCode("GD0007").feeCode("HP001").amount(new BigDecimal("300000"))
                .paymentMethod("CASH").status("VOIDED").transactionType("PAYMENT")
                .voidedAt(LocalDateTime.of(2026, 9, 17, 10, 0)).voidReason("Nhập nhầm")
                .paymentDate(LocalDateTime.of(2026, 9, 17, 9, 0)).build();

        try (XSSFWorkbook workbook = open(
                service.paymentTransactionsWorkbook(List.of(row, refund, voided), "Tất cả giao dịch"))) {
            Sheet sheet = workbook.getSheet(FinanceExportService.SHEET_TRANSACTIONS);
            assertThat(headers(sheet)).startsWith("Mã giao dịch", "Số phiếu", "Loại", "Ngày thanh toán", "Số tiền",
                    "Thực thu (+/-)", "Hình thức", "Trạng thái", "Người nộp / nhận", "Giao dịch gốc");
            assertThat(headers(sheet)).endsWith("Người tạo", "Ngày hủy", "Lý do hủy");
            Row data = sheet.getRow(HEADER + 1);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("GD0005");
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("PT2609-0005");
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo("Thu tiền");
            assertThat(data.getCell(3).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 9, 15, 9, 30));
            assertThat(data.getCell(4).getNumericCellValue()).isEqualTo(500_000d);
            assertThat(data.getCell(5).getNumericCellValue()).isEqualTo(500_000d);
            assertThat(data.getCell(6).getStringCellValue()).isEqualTo("VietQR");
            assertThat(data.getCell(7).getStringCellValue()).isEqualTo("Thành công");
            assertThat(data.getCell(8).getStringCellValue()).isEqualTo("Phụ huynh A");
            assertThat(data.getCell(19).getStringCellValue()).isEqualTo("accountant1");

            Row refundRow = sheet.getRow(HEADER + 2);
            assertThat(refundRow.getCell(2).getStringCellValue()).isEqualTo("Hoàn tiền");
            assertThat(refundRow.getCell(4).getNumericCellValue()).isEqualTo(200_000d);
            assertThat(refundRow.getCell(5).getNumericCellValue()).isEqualTo(-200_000d);
            assertThat(refundRow.getCell(9).getStringCellValue()).isEqualTo("GD0005");

            Row voidedRow = sheet.getRow(HEADER + 3);
            assertThat(voidedRow.getCell(5).getNumericCellValue()).isZero();
            assertThat(voidedRow.getCell(7).getStringCellValue()).isEqualTo("Đã hủy");
            assertThat(voidedRow.getCell(20).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 9, 17, 10, 0));
            assertThat(voidedRow.getCell(21).getStringCellValue()).isEqualTo("Nhập nhầm");
        }
    }

    @Test
    void emptyListStillProducesHeaderRow() throws IOException {
        try (XSSFWorkbook workbook = open(service.paymentTransactionsWorkbook(List.of(), null))) {
            Sheet sheet = workbook.getSheet(FinanceExportService.SHEET_TRANSACTIONS);
            assertThat(sheet.getRow(1)).isNull();
            assertThat(headers(sheet)).hasSize(22);
            assertThat(sheet.getLastRowNum()).isEqualTo(HEADER);
        }
    }

    @Test
    void unknownCodesFallBackToRawValue() {
        assertThat(FinanceExportServiceImpl.label(FinanceExportServiceImpl.TRANSACTION_STATUS_LABELS, "VOIDED"))
                .isEqualTo("Đã hủy");
        assertThat(FinanceExportServiceImpl.label(FinanceExportServiceImpl.TRANSACTION_STATUS_LABELS, "EXPIRED"))
                .isEqualTo("EXPIRED");
        assertThat(FinanceExportServiceImpl.label(FinanceExportServiceImpl.TRANSACTION_STATUS_LABELS, null)).isNull();
        assertThat(FinanceExportServiceImpl.periodOf(2026, null)).isNull();
    }

    private static XSSFWorkbook open(byte[] content) throws IOException {
        assertThat(content).isNotEmpty();
        return new XSSFWorkbook(new ByteArrayInputStream(content));
    }

    private static List<String> sheetNames(XSSFWorkbook workbook) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            names.add(workbook.getSheetName(i));
        }
        return names;
    }

    private static List<String> headers(Sheet sheet) {
        Row header = sheet.getRow(HEADER);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            values.add(header.getCell(i).getStringCellValue());
        }
        return values;
    }
}
