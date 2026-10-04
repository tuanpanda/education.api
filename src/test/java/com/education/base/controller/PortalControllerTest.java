package com.education.base.controller;

import com.education.base.dto.response.PortalClassDto;
import com.education.base.dto.response.PortalMeResponse;
import com.education.base.service.PortalService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortalController.class)
@Import(WebMvcSecurityTestConfig.class)
class PortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortalService portalService;

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(portalService);
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void me_asStudent_returnsProfile() throws Exception {
        when(portalService.me()).thenReturn(PortalMeResponse.builder()
                .studentId(42L).studentCode("HS42").fullName("Nguyễn Văn A").dateOfBirth(LocalDate.of(2012, 5, 1))
                .classes(List.of(PortalClassDto.builder().classId(3L).classCode("L3").className("Lớp 3")
                        .status("ACTIVE").build()))
                .parentName("Nguyễn Văn B").phone("0900000000").email("a@example.com").build());

        mockMvc.perform(get("/api/v1/portal/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(42))
                .andExpect(jsonPath("$.data.studentCode").value("HS42"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("2012-05-01"))
                .andExpect(jsonPath("$.data.classes[0].classId").value(3))
                .andExpect(jsonPath("$.data.classes[0].classCode").value("L3"))
                .andExpect(jsonPath("$.data.classes[0].className").value("Lớp 3"))
                .andExpect(jsonPath("$.data.classes[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.parentName").value("Nguyễn Văn B"))
                .andExpect(jsonPath("$.data.phone").value("0900000000"))
                .andExpect(jsonPath("$.data.email").value("a@example.com"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT", mustChangePassword = true)
    void me_studentPendingPasswordChange_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        verifyNoInteractions(portalService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void me_asStaffAdmin_isStudentOnly() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        verifyNoInteractions(portalService);
    }

    @Test
    @WithAuthUser(userType = "PARENT", roles = "ROLE_ADMIN")
    void me_asParent_isStudentOnly() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void me_ignoresStudentIdFromQuery() throws Exception {
        when(portalService.me()).thenReturn(PortalMeResponse.builder().studentId(42L).classes(List.of()).build());

        mockMvc.perform(get("/api/v1/portal/me").param("studentId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(42));
    }
}
