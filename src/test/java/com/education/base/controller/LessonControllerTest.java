package com.education.base.controller;

import com.education.base.dto.response.ClassLessonProgressDto;
import com.education.base.dto.response.LessonDto;
import com.education.base.dto.response.LessonResourceDto;
import com.education.base.dto.response.LessonTreeDto;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.security.Permissions;
import com.education.base.service.lesson.LessonService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LessonController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class LessonControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    LessonService lessonService;

    @Test
    void tree_ok() throws Exception {
        when(lessonService.getTree("CLASS", 7L)).thenReturn(LessonTreeDto.builder().ownerType("CLASS").ownerId(7L)
                .sections(List.of()).inheritedSections(List.of()).canManage(true).build());
        mockMvc.perform(get("/api/v1/lms/lessons/tree").param("ownerType", "CLASS").param("ownerId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(7))
                .andExpect(jsonPath("$.data.canManage").value(true));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {Permissions.LESSON_VIEW, Permissions.LESSON_UPDATE})
    void teacherOfOtherClass_is403NotClassTeacher() throws Exception {
        when(lessonService.getById(5L)).thenThrow(new ForbiddenException("NOT_CLASS_TEACHER", "Bạn không phụ trách lớp này."));
        mockMvc.perform(get("/api/v1/lms/lessons/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CLASS_TEACHER"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {Permissions.LESSON_VIEW, Permissions.LESSON_CREATE,
            Permissions.LESSON_UPDATE})
    void teacherWithoutDelete_cannotDeleteLesson() throws Exception {
        mockMvc.perform(delete("/api/v1/lms/lessons/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/lms/lesson-sections/5"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lessonService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {Permissions.COURSE_VIEW})
    void courseViewer_canReadTree_butNotWrite() throws Exception {
        when(lessonService.getTree("COURSE", 1L)).thenReturn(LessonTreeDto.builder().ownerType("COURSE").ownerId(1L).build());
        mockMvc.perform(get("/api/v1/lms/lessons/tree").param("ownerType", "COURSE").param("ownerId", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/lms/lessons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":1,\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/lms/classes/7/lesson-progress"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_validatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/lms/lessons").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/lms/lessons/1/resources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"SCRIPT\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lessonService);
    }

    @Test
    void reorder_isNotConfusedWithId_andErrorsMap() throws Exception {
        when(lessonService.reorder(any())).thenThrow(new OracleBusinessException("LESSON_REORDER_INVALID", "x"));
        mockMvc.perform(put("/api/v1/lms/lessons/reorder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerType\":\"CLASS\",\"ownerId\":7,\"sections\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LESSON_REORDER_INVALID"));
        when(lessonService.getById(9L)).thenThrow(new OracleBusinessException("LESSON_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/lms/lessons/9")).andExpect(status().isNotFound());
    }

    @Test
    void publish_andUploadFile() throws Exception {
        when(lessonService.publish(3L)).thenReturn(LessonDto.builder().id(3L).status("PUBLISHED").availableNow(true).build());
        mockMvc.perform(post("/api/v1/lms/lessons/3/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        when(lessonService.uploadResourceFile(eq(3L), any(), isNull())).thenReturn(LessonResourceDto.builder().id(70L)
                .resourceType("FILE").build());
        mockMvc.perform(multipart("/api/v1/lms/lessons/3/resources/file")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceType").value("FILE"));
    }

    @Test
    void classProgress_statusesKeyedByLessonId() throws Exception {
        when(lessonService.getClassProgress(7L)).thenReturn(ClassLessonProgressDto.builder().classId(7L)
                .lessons(List.of()).rows(List.of(ClassLessonProgressDto.StudentRow.builder().studentId(42L)
                        .statuses(Map.of(11L, "COMPLETED")).build())).build());
        mockMvc.perform(get("/api/v1/lms/classes/7/lesson-progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].statuses['11']").value("COMPLETED"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void student_cannotCallStaffApi() throws Exception {
        mockMvc.perform(get("/api/v1/lms/lessons/tree").param("ownerType", "CLASS").param("ownerId", "7"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_ONLY"));
        verifyNoInteractions(lessonService);
    }
}
