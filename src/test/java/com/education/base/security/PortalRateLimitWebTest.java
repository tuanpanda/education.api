package com.education.base.security;

import com.education.base.config.AuthSecurityConfig;
import com.education.base.controller.PortalController;
import com.education.base.controller.StudentAccountController;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PortalMeResponse;
import com.education.base.service.PortalService;
import com.education.base.service.StudentAccountService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tích hợp stream A x stream B: quy tắc {@code app.security.rate-limit.rules.portal} ({@code RequestRateLimitFilter},
 * chạy SAU Spring Security, TRƯỚC {@link PermissionInterceptor}) và hàng rào loại tài khoản.
 * <ul>
 *   <li>Ngân sách cổng tính theo người dùng trên {@code /api/v1/portal/**} - vượt -> 429 (kể cả với request mà
 *   interceptor sẽ chặn 403: giới hạn được xét trước).</li>
 *   <li>Học sinh gọi API nhân viên: luôn 403 {@code STAFF_ONLY}, không tiêu ngân sách cổng.</li>
 * </ul>
 */
@WebMvcTest({PortalController.class, StudentAccountController.class})
@Import({WebMvcSecurityTestConfig.class, AuthSecurityConfig.class})
@TestPropertySource(properties = {
        "app.security.rate-limit.rules.portal.per-user=2",
        "app.security.rate-limit.rules.portal.per-ip=0"})
class PortalRateLimitWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortalService portalService;

    @MockBean
    private StudentAccountService studentAccountService;

    @Test
    @WithAuthUser(id = 501L, username = "hs00501", userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void student_portalBudgetPerUser_then429() throws Exception {
        when(portalService.me()).thenReturn(PortalMeResponse.builder().studentId(42L).classes(List.of()).build());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/portal/me")).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/portal/me"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @WithAuthUser(id = 502L, username = "hs00502", userType = "STUDENT", studentId = 43, roles = "ROLE_STUDENT",
            permissions = "MENU_STUDENT_ACCOUNT:VIEW")
    void student_onStaffApi_isAlwaysStaffOnly_andDoesNotConsumePortalBudget() throws Exception {
        when(portalService.me()).thenReturn(PortalMeResponse.builder().studentId(43L).classes(List.of()).build());
        when(studentAccountService.search(any())).thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/student-accounts/search"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("STAFF_ONLY"));
        }
        mockMvc.perform(get("/api/v1/portal/me")).andExpect(status().isOk());
    }

    @Test
    @WithAuthUser(id = 503L, username = "admin503", roles = "ROLE_ADMIN")
    void staff_onPortal_isStudentOnly_thenRateLimitedLikeAnyPortalCaller() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/portal/me"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        }
        mockMvc.perform(get("/api/v1/portal/me")).andExpect(status().isTooManyRequests());
    }

    @Test
    @WithAuthUser(id = 504L, username = "admin504", roles = "ROLE_ADMIN")
    void staffApis_areNotCoveredByPortalRule() throws Exception {
        when(studentAccountService.search(any())).thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/student-accounts/search")).andExpect(status().isOk());
        }
    }

    @Test
    @WithAuthUser(id = 505L, username = "hs00505", userType = "STUDENT", studentId = 44, roles = "ROLE_STUDENT",
            mustChangePassword = true)
    void student_pendingPasswordChange_portalIs403_andStillCounted() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/portal/me"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        }
        mockMvc.perform(get("/api/v1/portal/me")).andExpect(status().isTooManyRequests());
    }
}
