package com.education.base.controller;

import com.education.base.common.excel.StudentExcelHelper;
import com.education.base.dto.response.ImportRowErrorDto;
import com.education.base.dto.response.StudentImportResultResponse;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.StudentImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentImportController.class)
class StudentImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentImportService studentImportService;

    @Test
    void downloadTemplate_returnsXlsxAttachment() throws Exception {
        byte[] body = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(studentImportService.generateTemplate()).thenReturn(body);

        mockMvc.perform(get("/api/v1/students/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, StudentExcelHelper.XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("mau_import_hoc_sinh.xlsx")));
    }

    @Test
    void importStudents_returnsSummary() throws Exception {
        when(studentImportService.importStudents(any()))
                .thenReturn(StudentImportResultResponse.builder()
                        .totalRows(2)
                        .successCount(1)
                        .failureCount(1)
                        .errors(List.of(ImportRowErrorDto.builder()
                                .rowNumber(3)
                                .columnName("Mã học sinh")
                                .studentCode("HS01")
                                .errorMessage("Mã học sinh đã tồn tại trên hệ thống.")
                                .build()))
                        .build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "ds.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/students/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].rowNumber").value(3));

        verify(studentImportService).importStudents(any());
    }

    @Test
    void importStudents_rejectsInvalidType() throws Exception {
        when(studentImportService.importStudents(any()))
                .thenThrow(new OracleBusinessException("FILE_TYPE_INVALID", "Chỉ chấp nhận file Excel .xlsx."));

        MockMultipartFile file = new MockMultipartFile(
                "file", "ds.xls", "application/vnd.ms-excel", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/students/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_INVALID"));
    }
}
