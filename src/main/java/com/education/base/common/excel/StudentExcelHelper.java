package com.education.base.common.excel;

import com.education.base.dto.request.StudentImportRowDto;
import com.education.base.exception.OracleBusinessException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sinh file mẫu và đọc danh sách học sinh từ Excel {@code .xlsx} (Apache POI).
 */
@Component
public class StudentExcelHelper {

    public static final String TEMPLATE_FILE_NAME = "mau_import_hoc_sinh.xlsx";
    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final int MAX_DATA_ROWS = 1000;

    public static final String[] STATUS_VALUES = {"ACTIVE", "INACTIVE", "GRADUATED", "SUSPENDED"};

    public static final String SHEET_DATA = "DanhSach";
    public static final String SHEET_GUIDE = "HuongDan";
    public static final String SHEET_CLASS_CATALOG = "DanhMucLop";

    public static final String COL_STUDENT_CODE = "Mã học sinh";
    public static final String COL_FULL_NAME = "Họ và tên (*)";
    public static final String COL_CLASS_CODE = "Mã lớp học";
    public static final String COL_STATUS = "Trạng thái (*)";
    public static final String COL_EMAIL = "Email";
    public static final String COL_PHONE = "Số điện thoại";
    public static final String COL_DOB = "Ngày sinh";
    public static final String COL_PARENT = "Phụ huynh";
    public static final String COL_ADDRESS = "Địa chỉ";
    public static final String COL_NOTE = "Note";

    private static final String CLASS_CODE_RANGE = "CLASS_CODES";

    private static final String[] HEADERS = {
            COL_STUDENT_CODE, COL_FULL_NAME, COL_CLASS_CODE, COL_STATUS, COL_EMAIL, COL_PHONE,
            COL_DOB, COL_PARENT, COL_ADDRESS, COL_NOTE
    };

    /**
     * Dòng danh mục lớp đưa vào sheet {@link #SHEET_CLASS_CATALOG} (dropdown mã lớp).
     */
    public record ClassCatalogRow(String classCode, String className, String status, Integer gradeLevel) {}

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("d/M/yyyy");

    public byte[] generateStudentTemplate() {
        return generateStudentTemplate(List.of());
    }

    public byte[] generateStudentTemplate(List<ClassCatalogRow> classCatalog) {
        List<ClassCatalogRow> catalog = classCatalog == null ? List.of() : classCatalog;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = textStyle(workbook);

            Sheet data = workbook.createSheet(SHEET_DATA);
            Row header = data.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            writeSample(data, 1, textStyle,
                    "", "Nguyen Van A", "", "ACTIVE", "a.nguyen@edu.com",
                    "0797958563", "26/4/2012", "Mẹ", "Hòa Bình Hạ", "");
            writeSample(data, 2, textStyle,
                    "", "Tran Thi B", "", "ACTIVE", "",
                    "0867926431", "22/2/2012", "", "Đa Nguu", "");

            int statusCol = columnIndex(COL_STATUS);
            DataValidationHelper dvHelper = data.getDataValidationHelper();
            DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(STATUS_VALUES);
            CellRangeAddressList addressList = new CellRangeAddressList(1, MAX_DATA_ROWS, statusCol, statusCol);
            DataValidation validation = dvHelper.createValidation(constraint, addressList);
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("Trạng thái không hợp lệ",
                    "Chỉ chọn ACTIVE, INACTIVE, GRADUATED hoặc SUSPENDED.");
            validation.setSuppressDropDownArrow(false);
            data.addValidationData(validation);

            writeClassCatalogSheet(workbook, catalog, headerStyle, textStyle);
            addClassCodeDropdown(data, catalog.size());

            data.createFreezePane(0, 1);
            for (int i = 0; i < HEADERS.length; i++) {
                data.autoSizeColumn(i);
                int width = data.getColumnWidth(i);
                data.setColumnWidth(i, Math.min(Math.max(width + 512, 3500), 12000));
            }

            writeGuideSheet(workbook, headerStyle, textStyle);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new OracleBusinessException("EXCEL_TEMPLATE_ERROR",
                    "Không thể tạo file Excel mẫu.", e);
        }
    }

    public List<StudentImportRowDto> parseStudentsFromExcel(InputStream inputStream) {
        if (inputStream == null) {
            throw new OracleBusinessException("FILE_EMPTY", "File tải lên không được để trống.");
        }
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(SHEET_DATA);
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            if (sheet == null) {
                throw new OracleBusinessException("EXCEL_SHEET_MISSING",
                        "File Excel không có sheet dữ liệu.");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new OracleBusinessException("EXCEL_HEADER_MISSING",
                        "Thiếu dòng tiêu đề trong file Excel.");
            }
            Map<String, Integer> columns = mapHeaders(headerRow);
            requireColumn(columns, "studentCode", COL_STUDENT_CODE);
            requireColumn(columns, "fullName", COL_FULL_NAME);

            List<StudentImportRowDto> rows = new ArrayList<>();
            int last = sheet.getLastRowNum();
            if (last > MAX_DATA_ROWS) {
                throw new OracleBusinessException("EXCEL_TOO_MANY_ROWS",
                        "File vượt quá " + MAX_DATA_ROWS + " dòng dữ liệu.");
            }
            for (int i = 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (isEmptyRow(row, columns.size())) {
                    continue;
                }
                rows.add(StudentImportRowDto.builder()
                        .rowNumber(i + 1)
                        .studentCode(cell(row, columns.get("studentCode")))
                        .fullName(cell(row, columns.get("fullName")))
                        .status(cell(row, columns.get("status")))
                        .email(cell(row, columns.get("email")))
                        .phone(cell(row, columns.get("phone")))
                        .classCode(cell(row, columns.get("classCode")))
                        .dateOfBirth(cell(row, columns.get("dateOfBirth")))
                        .parentName(cell(row, columns.get("parentName")))
                        .address(cell(row, columns.get("address")))
                        .note(cell(row, columns.get("note")))
                        .build());
            }
            return rows;
        } catch (OracleBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new OracleBusinessException("EXCEL_READ_ERROR",
                    "Không đọc được file Excel. Chỉ chấp nhận định dạng .xlsx.", e);
        }
    }

    private void writeGuideSheet(Workbook workbook, CellStyle headerStyle, CellStyle textStyle) {
        Sheet guide = workbook.createSheet(SHEET_GUIDE);
        String[][] lines = {
                {"Cột", "Bắt buộc", "Ghi chú"},
                {COL_STUDENT_CODE, "Không", "Để trống: hệ thống tự sinh theo SYS_CODE_RULES (giống form Thêm mới). Nếu điền, tối đa 30 ký tự, không trùng trong file và chưa có trên hệ thống."},
                {COL_FULL_NAME, "Có", "Tối đa 150 ký tự."},
                {COL_CLASS_CODE, "Không", "Không bắt buộc. Chọn từ dropdown (sheet DanhMucLop) hoặc gõ mã lớp đang OPEN/ONGOING. Để trống = chưa xếp lớp."},
                {COL_STATUS, "Có", "Để trống mặc định ACTIVE. Dropdown: ACTIVE, INACTIVE, GRADUATED, SUSPENDED."},
                {COL_EMAIL, "Không", "Đúng định dạng email, tối đa 100 ký tự."},
                {COL_PHONE, "Không", "8–20 ký tự số và + ( ) . -"},
                {COL_DOB, "Không", "Ngày sinh, ví dụ 26/4/2012 hoặc 2012-04-26."},
                {COL_PARENT, "Không", "Mẹ / Bố / tên phụ huynh, tối đa 100 ký tự."},
                {COL_ADDRESS, "Không", "Tối đa 255 ký tự."},
                {COL_NOTE, "Không", "Tối đa 500 ký tự."},
                {"", "", ""},
                {"Lưu ý", "", "Chỉ import file .xlsx. Dòng lỗi không được lưu; dòng hợp lệ vẫn được ghi."}
        };
        for (int r = 0; r < lines.length; r++) {
            Row row = guide.createRow(r);
            for (int c = 0; c < lines[r].length; c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue(lines[r][c]);
                cell.setCellStyle(r == 0 ? headerStyle : textStyle);
            }
        }
        for (int i = 0; i < 3; i++) {
            guide.autoSizeColumn(i);
        }
    }

    private void writeClassCatalogSheet(
            Workbook workbook,
            List<ClassCatalogRow> catalog,
            CellStyle headerStyle,
            CellStyle textStyle) {
        Sheet sheet = workbook.createSheet(SHEET_CLASS_CATALOG);
        String[] headers = {"Mã lớp học", "Tên lớp", "Khối", "Trạng thái"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        if (catalog.isEmpty()) {
            Row empty = sheet.createRow(1);
            Cell cell = empty.createCell(0);
            cell.setCellValue("(Chưa có lớp OPEN/ONGOING — có thể gõ mã lớp nếu đã biết)");
            cell.setCellStyle(textStyle);
        } else {
            for (int i = 0; i < catalog.size(); i++) {
                ClassCatalogRow item = catalog.get(i);
                Row row = sheet.createRow(i + 1);
                Cell code = row.createCell(0);
                code.setCellValue(item.classCode() == null ? "" : item.classCode());
                code.setCellStyle(textStyle);
                Cell name = row.createCell(1);
                name.setCellValue(item.className() == null ? "" : item.className());
                name.setCellStyle(textStyle);
                Cell grade = row.createCell(2);
                if (item.gradeLevel() != null) {
                    grade.setCellValue(item.gradeLevel());
                } else {
                    grade.setCellValue("");
                }
                grade.setCellStyle(textStyle);
                Cell status = row.createCell(3);
                status.setCellValue(item.status() == null ? "" : item.status());
                status.setCellStyle(textStyle);
            }
            Name named = workbook.createName();
            named.setNameName(CLASS_CODE_RANGE);
            named.setRefersToFormula(SHEET_CLASS_CATALOG + "!$A$2:$A$" + (catalog.size() + 1));
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void addClassCodeDropdown(Sheet data, int catalogSize) {
        if (catalogSize <= 0) {
            return;
        }
        int classCol = columnIndex(COL_CLASS_CODE);
        DataValidationHelper dvHelper = data.getDataValidationHelper();
        DataValidationConstraint constraint = dvHelper.createFormulaListConstraint(CLASS_CODE_RANGE);
        CellRangeAddressList addressList = new CellRangeAddressList(1, MAX_DATA_ROWS, classCol, classCol);
        DataValidation validation = dvHelper.createValidation(constraint, addressList);
        validation.setEmptyCellAllowed(true);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
        validation.createErrorBox("Mã lớp học",
                "Chọn mã trong DanhMucLop hoặc để trống. Có thể gõ mã lớp OPEN/ONGOING khác.");
        validation.setSuppressDropDownArrow(false);
        data.addValidationData(validation);
    }

    private static int columnIndex(String header) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (HEADERS[i].equals(header)) {
                return i;
            }
        }
        throw new IllegalStateException("Không tìm thấy cột: " + header);
    }

    private void writeSample(Sheet sheet, int rowIndex, CellStyle style, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(style);
        return style;
    }

    private CellStyle textStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        applyBorder(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private void applyBorder(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        short last = headerRow.getLastCellNum();
        for (int i = 0; i < last; i++) {
            String raw = cell(headerRow, i);
            String key = headerKey(raw);
            if (key != null) {
                columns.putIfAbsent(key, i);
            }
        }
        return columns;
    }

    private String headerKey(String header) {
        String n = normalize(header);
        if (n.isEmpty()) {
            return null;
        }
        if (n.contains("ma hoc sinh") || n.contains("student code") || n.equals("studentcode")) {
            return "studentCode";
        }
        if (n.contains("ho va ten") || n.contains("full name") || n.equals("fullname")) {
            return "fullName";
        }
        if (n.contains("trang thai") || n.equals("status")) {
            return "status";
        }
        if (n.equals("email")) {
            return "email";
        }
        if (n.contains("so dien thoai") || n.equals("phone") || n.equals("sdt")) {
            return "phone";
        }
        if (n.contains("ma lop") || n.contains("class code") || n.equals("classcode")) {
            return "classCode";
        }
        if (n.contains("ngay sinh") || n.equals("dob") || n.contains("date of birth")) {
            return "dateOfBirth";
        }
        if (n.contains("phu huynh") || n.contains("parent")) {
            return "parentName";
        }
        if (n.contains("dia chi") || n.equals("address")) {
            return "address";
        }
        if (n.equals("note") || n.contains("ghi chu")) {
            return "note";
        }
        return null;
    }

    private void requireColumn(Map<String, Integer> columns, String key, String label) {
        if (!columns.containsKey(key)) {
            throw new OracleBusinessException("EXCEL_HEADER_MISSING",
                    "Thiếu cột bắt buộc: " + label);
        }
    }

    private boolean isEmptyRow(Row row, int columnCount) {
        if (row == null) {
            return true;
        }
        int limit = Math.max(columnCount, row.getLastCellNum());
        for (int i = 0; i < limit; i++) {
            if (!cell(row, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cell(Row row, Integer index) {
        if (row == null || index == null) {
            return "";
        }
        return readCell(row.getCell(index));
    }

    String readCell(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return switch (type) {
            case STRING -> trimToEmpty(cell.getStringCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield date.format(ISO_DATE);
                }
                yield numericToString(cell.getNumericCellValue());
            }
            default -> "";
        };
    }

    private String numericToString(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        if (decimal.scale() <= 0) {
            return decimal.toPlainString();
        }
        DecimalFormat format = new DecimalFormat("0.##########", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }

    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalDate.parse(value, ISO_DATE);
        } catch (Exception ignored) {
            // thử định dạng Việt Nam
        }
        try {
            return LocalDate.parse(value, VN_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String stripped = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace("đ", "d")
                .replace("Đ", "D");
        return stripped.toLowerCase(Locale.ROOT).replace("*", " ").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean isXlsxFilename(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    public static boolean isXlsxContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        return type.contains("spreadsheetml")
                || type.contains("excel")
                || type.equals("application/octet-stream")
                || type.equals("application/zip");
    }

    public static Set<String> allowedStatuses() {
        return Set.of(STATUS_VALUES);
    }
}
