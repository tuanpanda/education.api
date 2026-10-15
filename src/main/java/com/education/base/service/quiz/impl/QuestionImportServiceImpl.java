package com.education.base.service.quiz.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.file.SafeFileType;
import com.education.base.common.quiz.QuizConstants;
import com.education.base.common.quiz.QuizConstants.QuestionType;
import com.education.base.common.quiz.QuizErrorCodes;
import com.education.base.dto.request.QuestionUpsertRequest;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.QuestionImportResultDto;
import com.education.base.exception.BusinessDataException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.AuditService;
import com.education.base.service.FinanceExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GĐ4 - nhập câu hỏi từ Excel (BR-08). Lỗi cấp file -> {@code FILE_TYPE_NOT_ALLOWED} / {@code FILE_CONTENT_MISMATCH} /
 * {@code FILE_TOO_LARGE}; lỗi nội dung (> 500 dòng, sai mẫu, lỗi dòng) -> 400 {@code QUESTION_IMPORT_INVALID} kèm
 * {@code data.errors}. All-or-nothing: có lỗi thì không tạo câu nào.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportServiceImpl {

    static final List<String> HEADERS = List.of("Loai cau hoi", "Mon hoc", "Khoi", "Chu de", "Do kho (1-3)",
            "Chia se (PRIVATE/SHARED)", "Noi dung", "Giai thich", "Dap an dung",
            "Lua chon 1", "Lua chon 2", "Lua chon 3", "Lua chon 4", "Lua chon 5",
            "Lua chon 6", "Lua chon 7", "Lua chon 8", "Lua chon 9", "Lua chon 10");
    static final String SHEET = "Cau hoi";
    static final int OPTION_COL = 9;

    private final QuestionServiceImpl questionService;
    private final AuditService auditService;

    public ExportFileDto template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET);
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row h = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS.get(i));
                c.setCellStyle(bold);
                sheet.setColumnWidth(i, (i == 6 ? 40 : 16) * 256);
            }
            String[][] samples = {
                    {"SINGLE", "Toan", "6", "Phan so", "1", "PRIVATE", "1/2 + 1/2 = ?", "", "2", "0", "1", "2"},
                    {"MULTI", "Toan", "6", "So nguyen to", "2", "SHARED", "Chon cac so nguyen to", "", "1,3", "2", "4", "5"},
                    {"TRUE_FALSE", "Toan", "6", "So hoc", "1", "PRIVATE", "0 la so tu nhien", "", "1", "Dung", "Sai"},
                    {"SHORT_TEXT", "Van", "6", "Tu vung", "2", "PRIVATE", "Thu do Viet Nam?", "", "Ha Noi|Hà Nội"},
                    {"ESSAY", "Van", "6", "Nghi luan", "3", "PRIVATE", "Viet doan van 5 cau", ""}};
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    row.createCell(c).setCellValue(samples[r][c]);
                }
            }
            wb.write(out);
            return new ExportFileDto("mau_nhap_cau_hoi.xlsx", FinanceExportService.XLSX_CONTENT_TYPE, out.toByteArray());
        } catch (IOException e) {
            throw new OracleBusinessException("EXPORT_ERROR", "Không tạo được file mẫu.");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public QuestionImportResultDto importFile(MultipartFile file, boolean dryRun) {
        byte[] bytes = readChecked(file);
        List<QuestionUpsertRequest> requests = new ArrayList<>();
        List<QuestionImportResultDto.RowError> errors = new ArrayList<>();
        int total = parse(bytes, requests, errors);
        QuestionImportResultDto result = QuestionImportResultDto.builder().dryRun(dryRun).totalRows(total)
                .imported(0).errors(errors).build();
        if (!errors.isEmpty()) {
            throw new BusinessDataException(QuizErrorCodes.QUESTION_IMPORT_INVALID, "File nhập câu hỏi không hợp lệ.", result);
        }
        if (!dryRun) {
            questionService.createValidated(requests);
            result.setImported(requests.size());
            auditService.success(AuditActions.QUIZ_QUESTIONS_IMPORTED, AuditActions.RESOURCE_QUESTION, null,
                    Map.of("totalRows", total, "imported", requests.size()));
        }
        return result;
    }

    /** Lỗi cấp file: FILE_EMPTY / FILE_TOO_LARGE / FILE_TYPE_NOT_ALLOWED / FILE_CONTENT_MISMATCH. */
    static byte[] readChecked(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OracleBusinessException("FILE_EMPTY", "File rỗng.");
        }
        if (file.getSize() > QuizConstants.IMPORT_MAX_BYTES) {
            throw new OracleBusinessException("FILE_TOO_LARGE", "File nhập tối đa 2 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new OracleBusinessException("FILE_READ_ERROR", "Không đọc được file.");
        }
        if (bytes.length > QuizConstants.IMPORT_MAX_BYTES) {
            throw new OracleBusinessException("FILE_TOO_LARGE", "File nhập tối đa 2 MB.");
        }
        String ext = SafeFileType.extensionOfName(file.getOriginalFilename());
        if (!"xlsx".equalsIgnoreCase(ext)) {
            throw new OracleBusinessException("FILE_TYPE_NOT_ALLOWED", "Chỉ nhận file .xlsx.");
        }
        SafeFileType.detect(file.getOriginalFilename(),
                Arrays.copyOf(bytes, Math.min(bytes.length, SafeFileType.HEADER_BYTES)));
        return bytes;
    }

    /** @return số dòng dữ liệu (bỏ dòng trống). */
    int parse(byte[] bytes, List<QuestionUpsertRequest> out, List<QuestionImportResultDto.RowError> errors) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (wb.getNumberOfSheets() == 0) {
                throw templateInvalid("File không có sheet.");
            }
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = sheet.getRow(0);
            if (header == null || !headerOk(header, fmt)) {
                throw templateInvalid("Sai mẫu: dòng tiêu đề không khớp file mẫu.");
            }
            List<Row> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r != null && !blankRow(r, fmt)) {
                    rows.add(r);
                }
            }
            if (rows.size() > QuizConstants.IMPORT_MAX_ROWS) {
                throw templateInvalid("File có " + rows.size() + " dòng, tối đa " + QuizConstants.IMPORT_MAX_ROWS + " dòng.");
            }
            if (rows.isEmpty()) {
                throw templateInvalid("File không có dòng dữ liệu.");
            }
            for (Row r : rows) {
                QuestionUpsertRequest req = toRequest(r, fmt, errors);
                if (req != null) {
                    out.add(req);
                }
            }
            return rows.size();
        } catch (IOException | RuntimeException e) {
            if (e instanceof OracleBusinessException obe) {
                throw obe;
            }
            log.warn("Không đọc được file Excel câu hỏi: {}", e.getMessage());
            throw templateInvalid("Không đọc được nội dung file Excel.");
        }
    }

    private QuestionUpsertRequest toRequest(Row r, DataFormatter fmt, List<QuestionImportResultDto.RowError> errors) {
        int rowNo = r.getRowNum() + 1;
        int before = errors.size();
        String type = cell(r, 0, fmt).toUpperCase(Locale.ROOT);
        String subject = cell(r, 1, fmt);
        String grade = cell(r, 2, fmt);
        String topic = cell(r, 3, fmt);
        String diff = cell(r, 4, fmt);
        String vis = cell(r, 5, fmt).toUpperCase(Locale.ROOT);
        String content = cell(r, 6, fmt);
        String explanation = cell(r, 7, fmt);
        String answer = cell(r, 8, fmt);
        if (!QuestionType.ALL.contains(type)) {
            errors.add(err(rowNo, "type", "INVALID", "Loại câu hỏi phải là " + QuestionType.PATTERN.replace('|', '/')));
        }
        requireText(errors, rowNo, "subjectName", subject, 100);
        requireText(errors, rowNo, "gradeLevel", grade, 20);
        requireText(errors, rowNo, "topic", topic, 200);
        requireText(errors, rowNo, "content", content, QuizConstants.CONTENT_MAX);
        Integer difficulty = null;
        try {
            difficulty = Integer.valueOf(diff.replaceAll("\\.0+$", ""));
        } catch (NumberFormatException e) {
            // ghi lỗi bên dưới
        }
        if (difficulty == null || difficulty < 1 || difficulty > 3) {
            errors.add(err(rowNo, "difficulty", "INVALID", "Độ khó phải là 1, 2 hoặc 3."));
        }
        if (!vis.isEmpty() && !"PRIVATE".equals(vis) && !"SHARED".equals(vis)) {
            errors.add(err(rowNo, "visibility", "INVALID", "Chia sẻ phải là PRIVATE hoặc SHARED."));
        }
        if (errors.size() > before) {
            return null;
        }
        List<String> optionTexts = new ArrayList<>();
        for (int c = OPTION_COL; c < OPTION_COL + QuizConstants.OPTIONS_MAX; c++) {
            String o = cell(r, c, fmt);
            if (!o.isEmpty()) {
                optionTexts.add(o);
            }
        }
        if (QuestionType.TRUE_FALSE.equals(type) && optionTexts.isEmpty()) {
            optionTexts = List.of("Đúng", "Sai");
        }
        List<QuestionUpsertRequest.Option> options = new ArrayList<>();
        List<String> accepted = new ArrayList<>();
        if (QuestionType.SHORT_TEXT.equals(type)) {
            for (String a : answer.split("\\|")) {
                if (!a.isBlank()) {
                    accepted.add(a.strip());
                }
            }
        } else if (!QuestionType.ESSAY.equals(type)) {
            List<Integer> correct = new ArrayList<>();
            for (String p : answer.split("[,;\\s]+")) {
                if (p.isBlank()) {
                    continue;
                }
                String t = p.strip().replaceAll("\\.0+$", "");
                if (QuestionType.TRUE_FALSE.equals(type) && fold(t).equals("dung")) {
                    t = "1";
                } else if (QuestionType.TRUE_FALSE.equals(type) && fold(t).equals("sai")) {
                    t = "2";
                }
                try {
                    correct.add(Integer.valueOf(t));
                } catch (NumberFormatException e) {
                    errors.add(err(rowNo, "correctAnswer", "INVALID", "Đáp án đúng phải là số thứ tự lựa chọn, ví dụ 1,3."));
                    return null;
                }
            }
            for (int i = 0; i < optionTexts.size(); i++) {
                options.add(QuestionUpsertRequest.Option.builder().content(HtmlUtils.htmlEscape(optionTexts.get(i)))
                        .isCorrect(correct.contains(i + 1)).sortOrder(i).build());
            }
            int optionCount = optionTexts.size();
            if (correct.stream().anyMatch(c -> c < 1 || c > optionCount)) {
                errors.add(err(rowNo, "correctAnswer", QuizErrorCodes.QUESTION_INVALID_OPTIONS,
                        "Đáp án đúng trỏ tới lựa chọn không tồn tại."));
                return null;
            }
        } else if (!optionTexts.isEmpty()) {
            errors.add(err(rowNo, "options", QuizErrorCodes.QUESTION_INVALID_OPTIONS, "Câu tự luận không có lựa chọn."));
            return null;
        }
        QuestionUpsertRequest req = QuestionUpsertRequest.builder().type(type).subjectName(subject).gradeLevel(grade)
                .topic(topic).difficulty(difficulty).visibility(vis.isEmpty() ? "PRIVATE" : vis)
                .content(toHtml(content)).explanation(explanation.isEmpty() ? null : toHtml(explanation))
                .options(options).acceptedAnswers(accepted).build();
        try {
            QuestionServiceImpl.validateOptions(req);
        } catch (OracleBusinessException e) {
            errors.add(err(rowNo, "options", QuizErrorCodes.QUESTION_INVALID_OPTIONS,
                    "Số lựa chọn / đáp án đúng không hợp lệ với loại " + type + "."));
            return null;
        }
        return req;
    }

    private static boolean headerOk(Row header, DataFormatter fmt) {
        for (int i = 0; i < OPTION_COL; i++) {
            if (!fold(cell(header, i, fmt)).equals(fold(HEADERS.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean blankRow(Row r, DataFormatter fmt) {
        for (int c = 0; c < HEADERS.size(); c++) {
            if (!cell(r, c, fmt).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(Row r, int c, DataFormatter fmt) {
        Cell cell = r.getCell(c);
        return cell == null ? "" : fmt.formatCellValue(cell).strip();
    }

    private static void requireText(List<QuestionImportResultDto.RowError> errors, int row, String field, String v, int max) {
        if (v.isEmpty()) {
            errors.add(err(row, field, "REQUIRED", "Không được để trống."));
        } else if (v.length() > max) {
            errors.add(err(row, field, "TOO_LONG", "Tối đa " + max + " ký tự."));
        }
    }

    private static String toHtml(String text) {
        return "<p>" + HtmlUtils.htmlEscape(text).replace("\n", "<br>") + "</p>";
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static QuestionImportResultDto.RowError err(int row, String field, String code, String message) {
        return QuestionImportResultDto.RowError.builder().row(row).field(field).code(code).message(message).build();
    }

    private static OracleBusinessException templateInvalid(String msg) {
        return new OracleBusinessException(QuizErrorCodes.QUESTION_IMPORT_INVALID, msg);
    }
}
