package com.education.base.controller;

import com.education.base.exception.OracleBusinessException;
import com.education.base.service.homework.PortalFileService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortalFileController.class)
@Import(WebMvcSecurityTestConfig.class)
class PortalFileControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    PortalFileService portalFileService;

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void serve_passesDownloadFlag() throws Exception {
        when(portalFileService.serve(5L, true)).thenReturn(ResponseEntity.ok(new ByteArrayResource(new byte[]{1})));
        mockMvc.perform(get("/api/v1/portal/me/files/5").param("download", "true"))
                .andExpect(status().isOk());
        verify(portalFileService).serve(5L, true);
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void foreignFile_is404() throws Exception {
        when(portalFileService.serve(6L, false)).thenThrow(new OracleBusinessException("PORTAL_FILE_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/portal/me/files/6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTAL_FILE_NOT_FOUND"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void staff_isStudentOnly() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/files/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        verifyNoInteractions(portalFileService);
    }
}
