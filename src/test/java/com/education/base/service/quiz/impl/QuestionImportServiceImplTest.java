package com.education.base.service.quiz.impl;

import com.education.base.dto.response.QuestionImportResultDto;
import com.education.base.exception.BusinessDataException;
import com.education.base.service.AuditService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** GĐ4 AC-03/AC-34: lỗi cấp file dùng mã file chung; QUESTION_IMPORT_INVALID chỉ cho nội dung; all-or-nothing. */
class QuestionImportServiceImplTest {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    QuestionServiceImpl questionService = mock(QuestionServiceImpl.class);
    QuestionImportServiceImpl service = new QuestionImportServiceImpl(questionService, mock(AuditService.class));

    static List<String> row(String type, String answer, String... options) {
        List<String> r = new ArrayList<>(List.of(type, "Toán", "10", "Hàm số", "2", "PRIVATE", "Nội dung", "", answer));
        r.addAll(List.of(options));
        return r;
    }

    static byte[] xlsx(List<String> header, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Cau hoi");
            List<List<String>> all = new ArrayList<>();
            all.add(header);
            all.addAll(rows);
            for (int i = 0; i < all.size(); i++) {
                Row r = s.createRow(i);
                for (int c = 0; c < all.get(i).size(); c++) {
                    r.createCell(c).setCellValue(all.get(i).get(c));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, XLSX, bytes);
    }

    @Test
    void validFile_importsAll_QZ_B01() throws Exception {
        byte[] b = xlsx(QuestionImportServiceImpl.HEADERS, List.of(row("SINGLE", "2", "A", "B", "C"),
                row("MULTI", "1,3", "A", "B", "C"), row("TRUE_FALSE", "Đúng"), row("SHORT_TEXT", "Hà Nội|Ha Noi"), row("ESSAY", "")));
        QuestionImportResultDto r = service.importFile(file("q.xlsx", b), false);
        assertThat(r.getImported()).isEqualTo(5);
        assertThat(r.getErrors()).isEmpty();
        verify(questionService).createValidated(anyList());
    }

    @Test
    void dryRun_createsNothing_QZ_B03() throws Exception {
        byte[] b = xlsx(QuestionImportServiceImpl.HEADERS, List.of(row("SINGLE", "1", "A", "B")));
        QuestionImportResultDto r = service.importFile(file("q.xlsx", b), true);
        assertThat(r.getDryRun()).isTrue();
        assertThat(r.getImported()).isZero();
        verify(questionService, never()).createValidated(anyList());
    }

    @Test
    void badRows_allOrNothing_withRowErrors_QZ_B02() throws Exception {
        byte[] b = xlsx(QuestionImportServiceImpl.HEADERS, List.of(row("SINGLE", "1", "A", "B"),
                row("SINGLE", "1,2", "A", "B"), row("XYZ", "1", "A", "B")));
        assertThatThrownBy(() -> service.importFile(file("q.xlsx", b), false))
                .isInstanceOf(BusinessDataException.class).satisfies(ex -> {
                    BusinessDataException e = (BusinessDataException) ex;
                    assertThat(e.getErrorCode()).isEqualTo("QUESTION_IMPORT_INVALID");
                    QuestionImportResultDto d = (QuestionImportResultDto) e.getData();
                    assertThat(d.getErrors()).extracting(QuestionImportResultDto.RowError::getRow).containsExactly(3, 4);
                });
        verify(questionService, never()).createValidated(anyList());
    }

    @Test
    void wrongTemplate_isContentError_QZ_B08() throws Exception {
        byte[] b = xlsx(List.of("Ten", "Tuoi"), List.of(List.of("a", "b")));
        assertThatThrownBy(() -> service.importFile(file("q.xlsx", b), false)).extracting("errorCode")
                .isEqualTo("QUESTION_IMPORT_INVALID");
    }

    @Test
    void over500Rows_isContentError_500IsOk_QZ_B04() throws Exception {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            rows.add(row("ESSAY", ""));
        }
        assertThat(service.importFile(file("q.xlsx", xlsx(QuestionImportServiceImpl.HEADERS, rows)), true).getTotalRows())
                .isEqualTo(500);
        rows.add(row("ESSAY", ""));
        byte[] b = xlsx(QuestionImportServiceImpl.HEADERS, rows);
        assertThatThrownBy(() -> service.importFile(file("q.xlsx", b), false)).extracting("errorCode")
                .isEqualTo("QUESTION_IMPORT_INVALID");
    }

    @Test
    void fileLevelErrors_useSharedFileCodes_QZ_B05_B09_B10() throws Exception {
        assertThatThrownBy(() -> service.importFile(file("q.csv", "a,b".getBytes()), false)).extracting("errorCode")
                .isEqualTo("FILE_TYPE_NOT_ALLOWED");
        assertThatThrownBy(() -> service.importFile(file("q.xlsx", "MZ fake exe content".getBytes()), false))
                .extracting("errorCode").isEqualTo("FILE_CONTENT_MISMATCH");
        assertThatThrownBy(() -> service.importFile(file("q.xlsx", new byte[2 * 1024 * 1024 + 1]), false))
                .extracting("errorCode").isEqualTo("FILE_TOO_LARGE");
        verify(questionService, never()).createValidated(anyList());
    }
}
