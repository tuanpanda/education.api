package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
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
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.FinanceExportService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Triển khai {@link FinanceExportService} bằng {@link SXSSFWorkbook} (ghi luồng, giữ bộ nhớ thấp với danh sách lớn).
 * Chỉ định dạng dữ liệu đã có; không truy cập Database.
 */
@Service
public class FinanceExportServiceImpl implements FinanceExportService {

    private static final int ROW_WINDOW = 200;
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final Map<String, String> FEE_STATUS_LABELS = Map.of(
            "UNPAID", "Chưa thu",
            "PARTIAL", "Thu một phần",
            "PAID", "Đã thu đủ",
            "OVERDUE", "Quá hạn",
            "CANCELLED", "Đã hủy");

    static final Map<String, String> TRANSACTION_STATUS_LABELS = Map.of(
            "PENDING", "Chờ xử lý",
            "SUCCESS", "Thành công",
            "FAILED", "Thất bại",
            "REFUNDED", "Đã hoàn tiền",
            "VOIDED", "Đã hủy");

    static final Map<String, String> TRANSACTION_TYPE_LABELS = Map.of(
            "PAYMENT", "Thu tiền",
            "REFUND", "Hoàn tiền");

    static final Map<String, String> PAYMENT_METHOD_LABELS = Map.of(
            "CASH", "Tiền mặt",
            "BANK_TRANSFER", "Chuyển khoản",
            "VIETQR", "VietQR",
            "CARD", "Thẻ",
            "EWALLET", "Ví điện tử");

    static final Map<String, String> STUDENT_STATUS_LABELS = Map.of(
            "ACTIVE", "Đang học",
            "INACTIVE", "Ngừng học",
            "GRADUATED", "Đã tốt nghiệp",
            "SUSPENDED", "Tạm dừng",
            "DELETED", "Đã xóa");

    static final Map<String, String> AGING_BUCKET_LABELS = Map.of(
            DomainConstants.DEBT_AGING_NOT_DUE, "Chưa tới hạn",
            DomainConstants.DEBT_AGING_D0_30, "Quá hạn 1-30 ngày",
            DomainConstants.DEBT_AGING_D31_60, "Quá hạn 31-60 ngày",
            DomainConstants.DEBT_AGING_D61_90, "Quá hạn 61-90 ngày",
            DomainConstants.DEBT_AGING_D90_PLUS, "Quá hạn trên 90 ngày");

    /** Một cột của bảng: tiêu đề, cách lấy giá trị, kiểu hiển thị, độ rộng (ký tự). */
    record Column<T>(String header, Function<T, Object> value, CellKind kind, int width) {

        static <T> Column<T> text(String header, Function<T, Object> value, int width) {
            return new Column<>(header, value, CellKind.TEXT, width);
        }

        static <T> Column<T> money(String header, Function<T, Object> value) {
            return new Column<>(header, value, CellKind.MONEY, 16);
        }

        static <T> Column<T> number(String header, Function<T, Object> value) {
            return new Column<>(header, value, CellKind.NUMBER, 10);
        }

        static <T> Column<T> percent(String header, Function<T, Object> value) {
            return new Column<>(header, value, CellKind.PERCENT, 12);
        }

        static <T> Column<T> date(String header, Function<T, Object> value) {
            return new Column<>(header, value, CellKind.DATE, 12);
        }

        static <T> Column<T> dateTime(String header, Function<T, Object> value) {
            return new Column<>(header, value, CellKind.DATE_TIME, 17);
        }
    }

    enum CellKind { TEXT, MONEY, NUMBER, PERCENT, DATE, DATE_TIME }

    /** Kiểu ô dùng chung trong một workbook. */
    private record Styles(CellStyle title, CellStyle subtitle, CellStyle header, CellStyle text, CellStyle money,
                          CellStyle number, CellStyle percent, CellStyle date, CellStyle dateTime,
                          CellStyle totalText, CellStyle totalMoney) {
    }

    /** Dòng "chỉ tiêu - giá trị" của sheet tổng hợp. */
    record Metric(String label, Object value, CellKind kind) {
    }

    // ------------------------------------------------------------------------------------------ báo cáo

    @Override
    public byte[] financeSummaryWorkbook(FinanceSummaryDto summary) {
        FinanceSummaryDto data = summary == null ? new FinanceSummaryDto() : summary;
        String period = periodLabel(data.getFromDate(), data.getToDate());
        return write(workbook -> {
            Styles styles = styles(workbook);
            List<Metric> metrics = List.of(
                    new Metric("Tổng tiền đã lập", data.getTotalBilled(), CellKind.MONEY),
                    new Metric("Miễn giảm", data.getTotalDiscount(), CellKind.MONEY),
                    new Metric("Phải thu sau miễn giảm", data.getNetBilled(), CellKind.MONEY),
                    new Metric("Thực thu (sau hoàn tiền)", data.getTotalCollected(), CellKind.MONEY),
                    new Metric("Đã hoàn tiền", data.getTotalRefunded(), CellKind.MONEY),
                    new Metric("Số giao dịch thu", data.getTransactionCount(), CellKind.NUMBER),
                    new Metric("Còn phải thu", data.getTotalOutstanding(), CellKind.MONEY),
                    new Metric("Còn phải thu quá hạn", data.getOverdueAmount(), CellKind.MONEY),
                    new Metric("Số khoản quá hạn", data.getOverdueFees(), CellKind.NUMBER),
                    new Metric("Số khoản phí (chưa hủy)", data.getFeeCount(), CellKind.NUMBER));
            writeTable(workbook, styles, SHEET_SUMMARY, "Tổng hợp tài chính", period, List.of(
                    Column.<Metric>text("Chỉ tiêu", Metric::label, 36),
                    new Column<Metric>("Giá trị", Metric::value, null, 20)), metrics, null);

            writeTable(workbook, styles, SHEET_STATUS, "Khoản phí theo trạng thái", period, List.of(
                    Column.<FeeStatusSummaryDto>text("Trạng thái", r -> label(FEE_STATUS_LABELS, r.getStatus()), 18),
                    Column.<FeeStatusSummaryDto>number("Số khoản", FeeStatusSummaryDto::getFeeCount),
                    Column.<FeeStatusSummaryDto>money("Phải thu sau miễn giảm", FeeStatusSummaryDto::getNetAmount),
                    Column.<FeeStatusSummaryDto>money("Còn phải thu", FeeStatusSummaryDto::getRemainingAmount)),
                    nullSafe(data.getStatusBreakdown()), null);

            writeTable(workbook, styles, SHEET_MONTHLY, "Phải thu và thực thu theo tháng", period, List.of(
                    Column.<FinanceMonthlyDto>text("Tháng", FinanceMonthlyDto::getMonth, 10),
                    Column.<FinanceMonthlyDto>money("Phải thu (theo kỳ)", FinanceMonthlyDto::getBilledAmount),
                    Column.<FinanceMonthlyDto>number("Số khoản", FinanceMonthlyDto::getFeeCount),
                    Column.<FinanceMonthlyDto>money("Thực thu", FinanceMonthlyDto::getCollectedAmount),
                    Column.<FinanceMonthlyDto>money("Đã hoàn", FinanceMonthlyDto::getRefundedAmount),
                    Column.<FinanceMonthlyDto>number("Số giao dịch thu", FinanceMonthlyDto::getTransactionCount)),
                    nullSafe(data.getMonthly()), null);
        });
    }

    @Override
    public byte[] debtAgingWorkbook(DebtAgingDto aging) {
        DebtAgingDto data = aging == null ? new DebtAgingDto() : aging;
        String asOf = "Ngày chốt: " + formatDate(data.getAsOfDate());
        return write(workbook -> {
            Styles styles = styles(workbook);
            List<Metric> buckets = List.of(
                    new Metric(AGING_BUCKET_LABELS.get(DomainConstants.DEBT_AGING_NOT_DUE), data.getNotDueAmount(), CellKind.MONEY),
                    new Metric(AGING_BUCKET_LABELS.get(DomainConstants.DEBT_AGING_D0_30), data.getDue0To30Amount(), CellKind.MONEY),
                    new Metric(AGING_BUCKET_LABELS.get(DomainConstants.DEBT_AGING_D31_60), data.getDue31To60Amount(), CellKind.MONEY),
                    new Metric(AGING_BUCKET_LABELS.get(DomainConstants.DEBT_AGING_D61_90), data.getDue61To90Amount(), CellKind.MONEY),
                    new Metric(AGING_BUCKET_LABELS.get(DomainConstants.DEBT_AGING_D90_PLUS), data.getDueOver90Amount(), CellKind.MONEY),
                    new Metric("Tổng còn phải thu", data.getTotalOutstanding(), CellKind.MONEY),
                    new Metric("Số khoản còn nợ", data.getFeeCount(), CellKind.NUMBER),
                    new Metric("Số học sinh còn nợ", data.getStudentCount(), CellKind.NUMBER));
            writeTable(workbook, styles, SHEET_SUMMARY, "Tuổi nợ học phí", asOf, List.of(
                    Column.<Metric>text("Nhóm tuổi nợ", Metric::label, 30),
                    new Column<Metric>("Còn phải thu", Metric::value, null, 20)), buckets, null);

            writeTable(workbook, styles, SHEET_AGING_STUDENTS, "Tuổi nợ theo học sinh", asOf, List.of(
                    Column.<DebtAgingStudentDto>text("Mã học sinh", DebtAgingStudentDto::getStudentCode, 14),
                    Column.<DebtAgingStudentDto>text("Họ và tên", DebtAgingStudentDto::getStudentName, 26),
                    Column.<DebtAgingStudentDto>text("Trạng thái HS", r -> label(STUDENT_STATUS_LABELS, r.getStudentStatus()), 14),
                    Column.<DebtAgingStudentDto>money("Chưa tới hạn", DebtAgingStudentDto::getNotDueAmount),
                    Column.<DebtAgingStudentDto>money("1-30 ngày", DebtAgingStudentDto::getDue0To30Amount),
                    Column.<DebtAgingStudentDto>money("31-60 ngày", DebtAgingStudentDto::getDue31To60Amount),
                    Column.<DebtAgingStudentDto>money("61-90 ngày", DebtAgingStudentDto::getDue61To90Amount),
                    Column.<DebtAgingStudentDto>money("Trên 90 ngày", DebtAgingStudentDto::getDueOver90Amount),
                    Column.<DebtAgingStudentDto>money("Tổng còn nợ", DebtAgingStudentDto::getTotalOutstanding),
                    Column.<DebtAgingStudentDto>number("Số khoản", DebtAgingStudentDto::getFeeCount),
                    Column.<DebtAgingStudentDto>date("Hạn thu sớm nhất", DebtAgingStudentDto::getOldestDueDate),
                    Column.<DebtAgingStudentDto>number("Số ngày quá hạn tối đa", DebtAgingStudentDto::getMaxDaysPastDue)),
                    nullSafe(data.getStudents()), null);

            writeTable(workbook, styles, SHEET_AGING_FEES, "Chi tiết khoản phí còn nợ", asOf, List.of(
                    Column.<DebtAgingFeeDto>text("Mã khoản phí", DebtAgingFeeDto::getFeeCode, 16),
                    Column.<DebtAgingFeeDto>text("Mã học sinh", DebtAgingFeeDto::getStudentCode, 14),
                    Column.<DebtAgingFeeDto>text("Họ và tên", DebtAgingFeeDto::getStudentName, 26),
                    Column.<DebtAgingFeeDto>text("Trạng thái HS", r -> label(STUDENT_STATUS_LABELS, r.getStudentStatus()), 14),
                    Column.<DebtAgingFeeDto>text("Lớp", r -> classLabel(r.getClassCode(), r.getClassName()), 24),
                    Column.<DebtAgingFeeDto>text("Kỳ thu", r -> periodOf(r.getFeeYear(), r.getFeeMonth()), 10),
                    Column.<DebtAgingFeeDto>date("Hạn thu", DebtAgingFeeDto::getDueDate),
                    Column.<DebtAgingFeeDto>text("Trạng thái", r -> label(FEE_STATUS_LABELS, r.getStatus()), 14),
                    Column.<DebtAgingFeeDto>money("Phải thu", DebtAgingFeeDto::getNetAmount),
                    Column.<DebtAgingFeeDto>money("Đã thu", DebtAgingFeeDto::getPaidAmount),
                    Column.<DebtAgingFeeDto>money("Còn nợ", DebtAgingFeeDto::getRemainingAmount),
                    Column.<DebtAgingFeeDto>number("Số ngày quá hạn", DebtAgingFeeDto::getDaysPastDue),
                    Column.<DebtAgingFeeDto>text("Nhóm tuổi nợ", r -> label(AGING_BUCKET_LABELS, r.getAgingBucket()), 22)),
                    nullSafe(data.getFees()), null);
        });
    }

    @Override
    public byte[] classCollectionWorkbook(ClassCollectionReportDto report) {
        ClassCollectionReportDto data = report == null ? new ClassCollectionReportDto() : report;
        String period = data.getMonth() == null
                ? "Năm " + data.getYear()
                : "Tháng " + String.format("%02d/%s", data.getMonth(), data.getYear());
        return write(workbook -> {
            Styles styles = styles(workbook);
            writeTable(workbook, styles, SHEET_CLASS_COLLECTION, "Thu tiền theo lớp", period, List.of(
                    Column.<ClassCollectionDto>text("Mã lớp", ClassCollectionDto::getClassCode, 14),
                    Column.<ClassCollectionDto>text("Tên lớp", ClassCollectionDto::getClassName, 28),
                    Column.<ClassCollectionDto>number("Số khoản", ClassCollectionDto::getFeeCount),
                    Column.<ClassCollectionDto>number("Số học sinh", ClassCollectionDto::getStudentCount),
                    Column.<ClassCollectionDto>money("Tổng tiền đã lập", ClassCollectionDto::getBilledAmount),
                    Column.<ClassCollectionDto>money("Miễn giảm", ClassCollectionDto::getDiscountAmount),
                    Column.<ClassCollectionDto>money("Phải thu", ClassCollectionDto::getNetAmount),
                    Column.<ClassCollectionDto>money("Thực thu", ClassCollectionDto::getCollectedAmount),
                    Column.<ClassCollectionDto>money("Còn phải thu", ClassCollectionDto::getOutstandingAmount),
                    Column.<ClassCollectionDto>percent("Tỷ lệ thu (%)", ClassCollectionDto::getCollectionRate)),
                    nullSafe(data.getRows()).stream().map(FinanceExportServiceImpl::withNoClassLabel).toList(),
                    data.getTotal() == null ? null : withTotalLabel(data.getTotal()));
        });
    }

    @Override
    public byte[] tuitionFeesWorkbook(List<FeeExportRowDto> rows, String filterDescription) {
        return write(workbook -> {
            Styles styles = styles(workbook);
            writeTable(workbook, styles, SHEET_FEES, "Danh sách khoản học phí", filterDescription, List.of(
                    Column.<FeeExportRowDto>text("Mã khoản phí", FeeExportRowDto::getFeeCode, 16),
                    Column.<FeeExportRowDto>text("Mã học sinh", FeeExportRowDto::getStudentCode, 14),
                    Column.<FeeExportRowDto>text("Họ và tên", FeeExportRowDto::getStudentName, 26),
                    Column.<FeeExportRowDto>text("Trạng thái HS", r -> label(STUDENT_STATUS_LABELS, r.getStudentStatus()), 14),
                    Column.<FeeExportRowDto>text("Mã lớp", FeeExportRowDto::getClassCode, 14),
                    Column.<FeeExportRowDto>text("Tên lớp", FeeExportRowDto::getClassName, 24),
                    Column.<FeeExportRowDto>text("Kỳ thu", r -> periodOf(r.getFeeYear(), r.getFeeMonth()), 10),
                    Column.<FeeExportRowDto>money("Tổng tiền", FeeExportRowDto::getTotalAmount),
                    Column.<FeeExportRowDto>money("Miễn giảm", FeeExportRowDto::getDiscountAmount),
                    Column.<FeeExportRowDto>money("Đã thu", FeeExportRowDto::getPaidAmount),
                    Column.<FeeExportRowDto>money("Còn phải thu", FeeExportRowDto::getRemainingAmount),
                    Column.<FeeExportRowDto>date("Hạn thu", FeeExportRowDto::getDueDate),
                    Column.<FeeExportRowDto>text("Trạng thái", r -> label(FEE_STATUS_LABELS, r.getStatus()), 14),
                    Column.<FeeExportRowDto>text("Ghi chú", FeeExportRowDto::getNote, 30),
                    Column.<FeeExportRowDto>dateTime("Ngày lập", FeeExportRowDto::getCreatedAt)),
                    nullSafe(rows), null);
        });
    }

    @Override
    public byte[] paymentTransactionsWorkbook(List<TransactionExportRowDto> rows, String filterDescription) {
        return write(workbook -> {
            Styles styles = styles(workbook);
            writeTable(workbook, styles, SHEET_TRANSACTIONS, "Danh sách giao dịch thanh toán", filterDescription, List.of(
                    Column.<TransactionExportRowDto>text("Mã giao dịch", TransactionExportRowDto::getTransactionCode, 20),
                    Column.<TransactionExportRowDto>text("Số phiếu", TransactionExportRowDto::getReceiptNo, 16),
                    Column.<TransactionExportRowDto>text("Loại", r -> label(TRANSACTION_TYPE_LABELS, r.getTransactionType()), 12),
                    Column.<TransactionExportRowDto>dateTime("Ngày thanh toán", TransactionExportRowDto::getPaymentDate),
                    Column.<TransactionExportRowDto>money("Số tiền", TransactionExportRowDto::getAmount),
                    Column.<TransactionExportRowDto>money("Thực thu (+/-)", TransactionExportRowDto::getNetAmount),
                    Column.<TransactionExportRowDto>text("Hình thức", r -> label(PAYMENT_METHOD_LABELS, r.getPaymentMethod()), 14),
                    Column.<TransactionExportRowDto>text("Trạng thái", r -> label(TRANSACTION_STATUS_LABELS, r.getStatus()), 14),
                    Column.<TransactionExportRowDto>text("Người nộp / nhận", TransactionExportRowDto::getPayerName, 22),
                    Column.<TransactionExportRowDto>text("Giao dịch gốc", TransactionExportRowDto::getRefTransactionCode, 20),
                    Column.<TransactionExportRowDto>text("Mã khoản phí", TransactionExportRowDto::getFeeCode, 16),
                    Column.<TransactionExportRowDto>text("Mã học sinh", TransactionExportRowDto::getStudentCode, 14),
                    Column.<TransactionExportRowDto>text("Họ và tên", TransactionExportRowDto::getStudentName, 26),
                    Column.<TransactionExportRowDto>text("Mã lớp", TransactionExportRowDto::getClassCode, 14),
                    Column.<TransactionExportRowDto>text("Tên lớp", TransactionExportRowDto::getClassName, 24),
                    Column.<TransactionExportRowDto>text("Mã NH (BIN)", TransactionExportRowDto::getBankBin, 10),
                    Column.<TransactionExportRowDto>text("Số tài khoản", TransactionExportRowDto::getAccountNo, 16),
                    Column.<TransactionExportRowDto>text("Mã tham chiếu NH", TransactionExportRowDto::getBankReferenceNo, 20),
                    Column.<TransactionExportRowDto>text("Ghi chú", TransactionExportRowDto::getNote, 30),
                    Column.<TransactionExportRowDto>text("Người tạo", TransactionExportRowDto::getCreatedBy, 14),
                    Column.<TransactionExportRowDto>dateTime("Ngày hủy", TransactionExportRowDto::getVoidedAt),
                    Column.<TransactionExportRowDto>text("Lý do hủy", TransactionExportRowDto::getVoidReason, 30)),
                    nullSafe(rows), null);
        });
    }

    // ------------------------------------------------------------------------------------------ ghi sheet

    @FunctionalInterface
    private interface WorkbookWriter {
        void fill(Workbook workbook);
    }

    private byte[] write(WorkbookWriter writer) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.fill(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new OracleBusinessException("EXCEL_EXPORT_ERROR", "Không thể tạo file Excel.", e);
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
                // Workbook trong bộ nhớ: lỗi đóng không ảnh hưởng nội dung đã ghi.
            }
        }
    }

    private <T> void writeTable(Workbook workbook, Styles styles, String sheetName, String title, String subtitle,
                                List<Column<T>> columns, List<T> rows, T totalRow) {
        Sheet sheet = workbook.createSheet(sheetName);
        Cell titleCell = sheet.createRow(0).createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.title());
        if (subtitle != null && !subtitle.isBlank()) {
            Cell subtitleCell = sheet.createRow(1).createCell(0);
            subtitleCell.setCellValue(subtitle);
            subtitleCell.setCellStyle(styles.subtitle());
        }

        Row header = sheet.createRow(HEADER_ROW_INDEX);
        header.setHeightInPoints(20);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns.get(i).header());
            cell.setCellStyle(styles.header());
            sheet.setColumnWidth(i, Math.min(columns.get(i).width(), 60) * 256 + 512);
        }

        int rowIndex = HEADER_ROW_INDEX + 1;
        for (T item : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                writeCell(row.createCell(i), columns.get(i), item, styles, false);
            }
        }
        if (totalRow != null) {
            Row row = sheet.createRow(rowIndex);
            for (int i = 0; i < columns.size(); i++) {
                writeCell(row.createCell(i), columns.get(i), totalRow, styles, true);
            }
        }
        sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
    }

    private <T> void writeCell(Cell cell, Column<T> column, T item, Styles styles, boolean total) {
        Object value = column.value().apply(item);
        CellKind kind = column.kind();
        if (kind == null && item instanceof Metric metric) {
            kind = metric.kind();
        }
        if (kind == null) {
            kind = CellKind.TEXT;
        }
        if (value == null) {
            cell.setCellStyle(total ? styles.totalText() : styles.text());
            return;
        }
        switch (kind) {
            case MONEY, NUMBER, PERCENT -> {
                if (value instanceof Number number) {
                    cell.setCellValue(number instanceof BigDecimal decimal ? decimal.doubleValue() : number.doubleValue());
                } else {
                    cell.setCellValue(String.valueOf(value));
                }
                if (total) {
                    cell.setCellStyle(kind == CellKind.MONEY ? styles.totalMoney() : styles.totalText());
                } else {
                    cell.setCellStyle(kind == CellKind.MONEY ? styles.money()
                            : kind == CellKind.PERCENT ? styles.percent() : styles.number());
                }
            }
            case DATE -> {
                if (value instanceof LocalDate date) {
                    cell.setCellValue(date);
                } else {
                    cell.setCellValue(String.valueOf(value));
                }
                cell.setCellStyle(styles.date());
            }
            case DATE_TIME -> {
                if (value instanceof LocalDateTime dateTime) {
                    cell.setCellValue(dateTime);
                } else {
                    cell.setCellValue(String.valueOf(value));
                }
                cell.setCellStyle(styles.dateTime());
            }
            default -> {
                cell.setCellValue(String.valueOf(value));
                cell.setCellStyle(total ? styles.totalText() : styles.text());
            }
        }
    }

    private Styles styles(Workbook workbook) {
        DataFormat format = workbook.createDataFormat();

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);

        Font italic = workbook.createFont();
        italic.setItalic(true);
        CellStyle subtitle = workbook.createCellStyle();
        subtitle.setFont(italic);

        Font bold = workbook.createFont();
        bold.setBold(true);
        bold.setColor(IndexedColors.WHITE.getIndex());
        CellStyle header = bordered(workbook);
        header.setFont(bold);
        header.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);

        CellStyle text = bordered(workbook);
        CellStyle money = bordered(workbook);
        money.setDataFormat(format.getFormat("#,##0"));
        CellStyle number = bordered(workbook);
        number.setDataFormat(format.getFormat("0"));
        CellStyle percent = bordered(workbook);
        percent.setDataFormat(format.getFormat("0.00"));
        CellStyle date = bordered(workbook);
        date.setDataFormat(format.getFormat("dd/mm/yyyy"));
        CellStyle dateTime = bordered(workbook);
        dateTime.setDataFormat(format.getFormat("dd/mm/yyyy hh:mm"));

        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        CellStyle totalText = bordered(workbook);
        totalText.setFont(totalFont);
        CellStyle totalMoney = bordered(workbook);
        totalMoney.setFont(totalFont);
        totalMoney.setDataFormat(format.getFormat("#,##0"));

        return new Styles(title, subtitle, header, text, money, number, percent, date, dateTime, totalText, totalMoney);
    }

    private static CellStyle bordered(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // ------------------------------------------------------------------------------------------ định dạng

    static String label(Map<String, String> labels, String code) {
        if (code == null) {
            return null;
        }
        return labels.getOrDefault(code, code);
    }

    static String periodOf(Integer year, Integer month) {
        if (year == null || month == null) {
            return null;
        }
        return String.format("%02d/%d", month, year);
    }

    static String periodLabel(LocalDate from, LocalDate to) {
        return "Từ ngày " + formatDate(from) + " đến ngày " + formatDate(to);
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "-" : date.format(VN_DATE);
    }

    private static String classLabel(String code, String name) {
        if (code == null && name == null) {
            return null;
        }
        if (code == null) {
            return name;
        }
        return name == null ? code : code + " - " + name;
    }

    private static ClassCollectionDto withNoClassLabel(ClassCollectionDto row) {
        if (row.getClassId() != null || row.getClassName() != null) {
            return row;
        }
        ClassCollectionDto copy = copyOf(row);
        copy.setClassName("(Không gắn lớp)");
        return copy;
    }

    private static ClassCollectionDto withTotalLabel(ClassCollectionDto total) {
        ClassCollectionDto copy = copyOf(total);
        copy.setClassCode("TỔNG CỘNG");
        copy.setClassName(null);
        return copy;
    }

    private static ClassCollectionDto copyOf(ClassCollectionDto row) {
        return ClassCollectionDto.builder()
                .classId(row.getClassId()).classCode(row.getClassCode()).className(row.getClassName())
                .classStatus(row.getClassStatus()).feeCount(row.getFeeCount()).studentCount(row.getStudentCount())
                .billedAmount(row.getBilledAmount()).discountAmount(row.getDiscountAmount())
                .netAmount(row.getNetAmount()).collectedAmount(row.getCollectedAmount())
                .outstandingAmount(row.getOutstandingAmount()).collectionRate(row.getCollectionRate())
                .build();
    }

    private static <T> List<T> nullSafe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
