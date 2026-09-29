package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.dto.request.StudentCreateRequest;
import com.education.base.dto.request.StudentFilterRequest;
import com.education.base.dto.request.StudentUpdateRequest;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.dto.response.StudentReportDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.StudentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StudentService studentService;

    @Test
    void search_returnsPagedDataWrappedInApiResponse() throws Exception {
        StudentReportDto row = StudentReportDto.builder()
                .id(1L)
                .studentCode("SV001")
                .fullName("Nguyen Van A")
                .email("a.nguyen@education.com")
                .status("ACTIVE")
                .build();
        when(studentService.search(any(StudentFilterRequest.class)))
                .thenReturn(PageResponse.of(List.of(row), 2, 5, 11));

        mockMvc.perform(get("/api/v1/students/search")
                        .param("keyword", "SV")
                        .param("status", "ACTIVE")
                        .param("pageNo", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.content[0].studentCode").value("SV001"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5))
                .andExpect(jsonPath("$.data.totalRows").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        ArgumentCaptor<StudentFilterRequest> captor = ArgumentCaptor.forClass(StudentFilterRequest.class);
        verify(studentService).search(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("SV");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void search_withoutParams_appliesDefaultPaging() throws Exception {
        when(studentService.search(any(StudentFilterRequest.class)))
                .thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/students/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalPages").value(0));

        ArgumentCaptor<StudentFilterRequest> captor = ArgumentCaptor.forClass(StudentFilterRequest.class);
        verify(studentService).search(captor.capture());
        assertThat(captor.getValue().resolvePageNo()).isEqualTo(1);
        assertThat(captor.getValue().resolvePageSize()).isEqualTo(20);
    }

    @Test
    void search_invalidStatus_returnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/students/search").param("status", "KHONG_TON_TAI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void search_pageSizeOverLimit_returnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/students/search").param("pageSize", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getDetail_returnsStudentWithAttachments() throws Exception {
        StudentDetailResponse detail = StudentDetailResponse.builder()
                .id(7L)
                .studentCode("SV007")
                .fullName("Le Thi C")
                .status("ACTIVE")
                .attachments(List.of(FileResponseDto.builder()
                        .id(3L)
                        .originalName("bang-diem.pdf")
                        .viewUrl("/api/v1/files/view/3")
                        .downloadUrl("/api/v1/files/download/3")
                        .build()))
                .build();
        when(studentService.getDetail(7L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/students/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCode").value("SV007"))
                .andExpect(jsonPath("$.data.attachments[0].originalName").value("bang-diem.pdf"))
                .andExpect(jsonPath("$.data.attachments[0].viewUrl").value("/api/v1/files/view/3"));
    }

    @Test
    void getDetail_notFound_returnsBusinessError() throws Exception {
        when(studentService.getDetail(99L))
                .thenThrow(new OracleBusinessException("STUDENT_NOT_FOUND", "Không tìm thấy học sinh với ID: 99"));

        mockMvc.perform(get("/api/v1/students/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void create_validRequest_returnsCreatedStudent() throws Exception {
        StudentCreateRequest request = new StudentCreateRequest("Pham Van D", "d.pham@edu.com", "ACTIVE");
        when(studentService.create(any(StudentCreateRequest.class)))
                .thenReturn(StudentDetailResponse.builder().id(10L).studentCode("SV20260001").build());

        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.message").value("Thêm mới học sinh thành công."))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.studentCode").value("SV20260001"));
    }

    @Test
    void create_blankFullName_returnsValidationError() throws Exception {
        StudentCreateRequest request = new StudentCreateRequest("  ", null, null);

        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_futureDateOfBirth_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Pham Van D","dateOfBirth":"2099-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_nestedNewClassMissingCode_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Pham Van D","newClass":{"courseName":"TOEIC"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void update_validRequest_returnsUpdatedStudent() throws Exception {
        StudentUpdateRequest request = new StudentUpdateRequest("Ten Moi", "moi@edu.com", "INACTIVE");
        when(studentService.update(eq(5L), any(StudentUpdateRequest.class)))
                .thenReturn(StudentDetailResponse.builder().id(5L).fullName("Ten Moi").status("INACTIVE").build());

        mockMvc.perform(put("/api/v1/students/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Ten Moi"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void update_blankFullName_returnsValidationError() throws Exception {
        StudentUpdateRequest request = new StudentUpdateRequest("", "moi@edu.com", "ACTIVE");

        mockMvc.perform(put("/api/v1/students/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void delete_callsSoftDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/students/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.message").value("Xóa học sinh thành công."));

        verify(studentService).softDelete(5L);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {"MENU_STUDENT_LIST:VIEW"})
    void delete_withoutDeletePermission_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/students/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        org.mockito.Mockito.verifyNoInteractions(studentService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {"MENU_STUDENT_LIST:VIEW", "MENU_STUDENT_LIST:DELETE"})
    void delete_withDeletePermission_isAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/students/5"))
                .andExpect(status().isOk());

        verify(studentService).softDelete(5L);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ACCOUNTANT", permissions = {"MENU_TUITION_FEE:VIEW"})
    void getDetail_requiresStudentView() throws Exception {
        mockMvc.perform(get("/api/v1/students/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadDocument_returnsFileMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ho-so.pdf", "application/pdf", "noi dung".getBytes());
        when(studentService.uploadDocument(eq(5L), any()))
                .thenReturn(FileResponseDto.builder()
                        .id(21L)
                        .originalName("ho-so.pdf")
                        .moduleName("STUDENT")
                        .referenceId(5L)
                        .viewUrl("/api/v1/files/view/21")
                        .build());

        mockMvc.perform(multipart("/api/v1/students/5/upload-document").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.moduleName").value("STUDENT"))
                .andExpect(jsonPath("$.data.referenceId").value(5));
    }
}
