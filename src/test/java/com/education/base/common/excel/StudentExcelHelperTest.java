package com.education.base.common.excel;

import com.education.base.dto.request.StudentImportRowDto;
import com.education.base.exception.OracleBusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentExcelHelperTest {

    private final StudentExcelHelper helper = new StudentExcelHelper();

    @Test
    void generateTemplate_roundTripParsesSampleRows() {
        byte[] xlsx = helper.generateStudentTemplate();
        assertThat(xlsx).isNotEmpty();
        assertThat(xlsx[0]).isEqualTo((byte) 0x50);
        assertThat(xlsx[1]).isEqualTo((byte) 0x4B);

        List<StudentImportRowDto> rows = helper.parseStudentsFromExcel(new ByteArrayInputStream(xlsx));
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().getStudentCode()).isEmpty();
        assertThat(rows.getFirst().getFullName()).isEqualTo("Nguyen Van A");
        assertThat(rows.getFirst().getStatus()).isEqualTo("ACTIVE");
        assertThat(rows.getFirst().getPhone()).isEqualTo("0797958563");
        assertThat(rows.getFirst().getClassCode()).isEmpty();
        assertThat(rows.get(1).getStudentCode()).isEmpty();
        assertThat(StudentExcelHelper.parseDate(rows.getFirst().getDateOfBirth()))
                .isEqualTo(LocalDate.of(2012, 4, 26));
    }

    @Test
    void generateTemplate_includesOptionalClassCatalogAndDropdownSheet() throws Exception {
        byte[] xlsx = helper.generateStudentTemplate(List.of(
                new StudentExcelHelper.ClassCatalogRow("LH920260001", "Lớp 9A", "OPEN", 9)));
        try (var in = new ByteArrayInputStream(xlsx);
             var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            var catalog = wb.getSheet(StudentExcelHelper.SHEET_CLASS_CATALOG);
            assertThat(catalog).isNotNull();
            assertThat(catalog.getRow(1).getCell(0).getStringCellValue()).isEqualTo("LH920260001");
            assertThat(wb.getSheet(StudentExcelHelper.SHEET_DATA).getRow(0).getCell(2).getStringCellValue())
                    .isEqualTo(StudentExcelHelper.COL_CLASS_CODE);
            assertThat(wb.getName("CLASS_CODES")).isNotNull();
        }
        List<StudentImportRowDto> rows = helper.parseStudentsFromExcel(new ByteArrayInputStream(xlsx));
        assertThat(rows.getFirst().getClassCode()).isEmpty();
    }

    @Test
    void parse_rejectsNonXlsxBytes() {
        assertThatThrownBy(() -> helper.parseStudentsFromExcel(
                new ByteArrayInputStream("not-excel".getBytes())))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isIn("EXCEL_READ_ERROR", "EXCEL_SHEET_MISSING", "EXCEL_HEADER_MISSING");
    }

    @Test
    void parseDate_supportsVietnameseAndIso() {
        assertThat(StudentExcelHelper.parseDate("26/4/2012")).isEqualTo(LocalDate.of(2012, 4, 26));
        assertThat(StudentExcelHelper.parseDate("2012-04-26")).isEqualTo(LocalDate.of(2012, 4, 26));
        assertThat(StudentExcelHelper.parseDate("abc")).isNull();
        assertThat(StudentExcelHelper.parseDate("")).isNull();
    }
}
