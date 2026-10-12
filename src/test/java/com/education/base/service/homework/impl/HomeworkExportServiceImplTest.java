package com.education.base.service.homework.impl;

import com.education.base.audit.AuditActions;
import com.education.base.dto.response.AssignmentDto;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.SubmissionMatrixDto;
import com.education.base.dto.response.SubmissionRowDto;
import com.education.base.service.AuditService;
import com.education.base.service.homework.HomeworkService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static com.education.base.service.homework.impl.HomeworkTestData.DUE;
import static com.education.base.service.homework.impl.HomeworkTestData.clockAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeworkExportServiceImplTest {

    @Test
    void export_headerAndRows() throws Exception {
        HomeworkService hs = mock(HomeworkService.class);
        AuditService audit = mock(AuditService.class);
        when(hs.getSubmissionMatrix(5L)).thenReturn(SubmissionMatrixDto.builder()
                .assignment(AssignmentDto.builder().id(5L).maxScore(BigDecimal.TEN).build())
                .rows(List.of(
                        SubmissionRowDto.builder().studentCode("HS1").studentName("An").state("GRADED")
                                .score(new BigDecimal("9")).attemptCount(1).submittedAt(DUE).fileCount(2).build(),
                        SubmissionRowDto.builder().studentCode("HS2").studentName("Binh").state("MISSING")
                                .attemptCount(0).fileCount(0).build()))
                .build());
        ExportFileDto f = new HomeworkExportServiceImpl(hs, audit, clockAt(DUE)).exportAssignment(5L);
        assertThat(f.fileName()).isEqualTo("bai_tap_5_20261010.xlsx");
        assertThat(f.contentType()).contains("spreadsheetml");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(f.content()))) {
            Sheet s = wb.getSheetAt(0);
            assertThat(s.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Mã HS");
            assertThat(s.getRow(0).getLastCellNum()).isEqualTo((short) HomeworkExportServiceImpl.HEADERS.size());
            assertThat(s.getLastRowNum()).isEqualTo(2);
            assertThat(s.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(9.0);
            assertThat(s.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Thiếu bài");
        }
        verify(audit).success(eq(AuditActions.HOMEWORK_EXPORTED), eq(AuditActions.RESOURCE_ASSIGNMENT), eq(5L), anyMap());
    }
}
