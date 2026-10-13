package com.education.base.controller;

import com.education.base.dto.response.AssignmentDto;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.SubmissionDetailDto;
import com.education.base.entity.SubmissionEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.security.Permissions;
import com.education.base.service.homework.HomeworkExportService;
import com.education.base.service.homework.HomeworkService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeworkController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class HomeworkControllerTest {

    static final String GRADE_BODY = """
            {"score": 8, "feedback": "ok", "expectedVersion": 3}
            """;

    @Autowired
    MockMvc mockMvc;
    @MockBean
    HomeworkService homeworkService;
    @MockBean
    HomeworkExportService homeworkExportService;

    @Test
    void search_returnsPage1Based() throws Exception {
        when(homeworkService.search(any())).thenReturn(PageResponse.of(
                List.of(AssignmentDto.builder().id(1L).title("BT").status("DRAFT").build()), 1, 20, 1));
        mockMvc.perform(get("/api/v1/lms/assignments").param("pageNo", "1").param("classId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.pageNo").value(1));
    }

    @Test
    void create_validatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/lms/assignments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(homeworkService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {Permissions.HOMEWORK_VIEW})
    void grade_withoutGradePermission_is403() throws Exception {
        mockMvc.perform(put("/api/v1/lms/assignments/submissions/10/grade")
                        .contentType(MediaType.APPLICATION_JSON).content(GRADE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(homeworkService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {Permissions.HOMEWORK_VIEW})
    void teacherOfOtherClass_is403NotClassTeacher() throws Exception {
        when(homeworkService.getById(5L)).thenThrow(new ForbiddenException("NOT_CLASS_TEACHER", "Bạn không phụ trách lớp này."));
        mockMvc.perform(get("/api/v1/lms/assignments/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CLASS_TEACHER"));
    }

    @Test
    void grade_versionConflict_is409() throws Exception {
        when(homeworkService.grade(eq(10L), any())).thenThrow(new OracleBusinessException("GRADE_CONFLICT", "x"));
        mockMvc.perform(put("/api/v1/lms/assignments/submissions/10/grade")
                        .contentType(MediaType.APPLICATION_JSON).content(GRADE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_CONFLICT"));
    }

    @Test
    void grade_optimisticLock_is409GradeConflict() throws Exception {
        when(homeworkService.grade(eq(10L), any())).thenThrow(
                new ObjectOptimisticLockingFailureException(SubmissionEntity.class, 10L));
        mockMvc.perform(put("/api/v1/lms/assignments/submissions/10/grade")
                        .contentType(MediaType.APPLICATION_JSON).content(GRADE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_CONFLICT"));
    }

    @Test
    void grade_ok_andNotFoundMapsTo404() throws Exception {
        when(homeworkService.grade(eq(10L), any())).thenReturn(SubmissionDetailDto.builder().id(10L).status("GRADED").build());
        mockMvc.perform(put("/api/v1/lms/assignments/submissions/10/grade")
                        .contentType(MediaType.APPLICATION_JSON).content(GRADE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADED"));
        when(homeworkService.getSubmission(11L)).thenThrow(new OracleBusinessException("SUBMISSION_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/lms/assignments/submissions/11"))
                .andExpect(status().isNotFound());
    }

    @Test
    void return_requiresReason() throws Exception {
        mockMvc.perform(post("/api/v1/lms/assignments/submissions/10/return")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(homeworkService);
    }

    @Test
    void export_returnsXlsxAttachment() throws Exception {
        when(homeworkExportService.exportAssignment(5L)).thenReturn(new ExportFileDto("bai_tap_5_20261012.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2}));
        mockMvc.perform(get("/api/v1/lms/assignments/5/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("bai_tap_5_20261012.xlsx")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void student_cannotCallStaffApi() throws Exception {
        mockMvc.perform(get("/api/v1/lms/assignments"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_ONLY"));
        verifyNoInteractions(homeworkService);
    }
}
