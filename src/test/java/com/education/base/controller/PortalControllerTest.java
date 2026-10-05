package com.education.base.controller;

import com.education.base.dto.response.PortalAttendanceDto;
import com.education.base.dto.response.PortalAttendanceSummaryDto;
import com.education.base.dto.response.PortalClassDetailDto;
import com.education.base.dto.response.PortalClassDto;
import com.education.base.dto.response.PortalDashboardResponse;
import com.education.base.dto.response.PortalFeeDetailDto;
import com.education.base.dto.response.PortalFeeListItemDto;
import com.education.base.dto.response.PortalGradeDto;
import com.education.base.dto.response.PortalMeResponse;
import com.education.base.dto.response.PortalOutstandingFeesSummaryDto;
import com.education.base.dto.response.PortalPaymentDto;
import com.education.base.dto.response.PortalTimetableItemDto;
import com.education.base.dto.response.PortalTimetableResponse;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.PortalService;
import com.education.base.service.impl.StudentScopeGuard;
import com.education.base.exception.GlobalExceptionHandler;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortalController.class)
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
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
                .studentId(42L).studentCode("HS42").fullName("Nguyen Van A").dateOfBirth(LocalDate.of(2012, 5, 1))
                .classes(List.of(PortalClassDto.builder().classId(3L).classCode("L3").className("Lop 3")
                        .status("ACTIVE").build()))
                .parentName("Nguyen Van B").phone("0900000000").email("a@example.com").build());

        mockMvc.perform(get("/api/v1/portal/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(42))
                .andExpect(jsonPath("$.data.studentCode").value("HS42"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("2012-05-01"))
                .andExpect(jsonPath("$.data.classes[0].classId").value(3))
                .andExpect(jsonPath("$.data.classes[0].classCode").value("L3"))
                .andExpect(jsonPath("$.data.classes[0].className").value("Lop 3"))
                .andExpect(jsonPath("$.data.classes[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.parentName").value("Nguyen Van B"))
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

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void dashboard_asStudent_ok() throws Exception {
        when(portalService.dashboard()).thenReturn(PortalDashboardResponse.builder()
                .outstandingFees(PortalOutstandingFeesSummaryDto.builder()
                        .outstandingCount(1).totalRemaining(new BigDecimal("100")).build())
                .unreadAnnouncements(0L).build());

        mockMvc.perform(get("/api/v1/portal/me/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingFees.outstandingCount").value(1))
                .andExpect(jsonPath("$.data.unreadAnnouncements").value(0));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void dashboard_asStaff_isStudentOnly() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void classes_asStudent_ok() throws Exception {
        when(portalService.myClasses()).thenReturn(List.of(PortalClassDetailDto.builder()
                .classId(3L).classCode("L3").className("Lop 3").subjectName("Toan")
                .teacherName("GV A").roomName("P1").build()));

        mockMvc.perform(get("/api/v1/portal/me/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subjectName").value("Toan"))
                .andExpect(jsonPath("$.data[0].teacherName").value("GV A"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void timetable_passesFromTo_ignoresClientStudentId() throws Exception {
        when(portalService.timetable(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(PortalTimetableResponse.builder()
                        .fromDate(LocalDate.of(2026, 10, 1))
                        .toDate(LocalDate.of(2026, 10, 31))
                        .items(List.of(PortalTimetableItemDto.builder().sessionId(1L).classCode("L3").build()))
                        .build());

        mockMvc.perform(get("/api/v1/portal/me/timetable")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31")
                        .param("studentId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sessionId").value(1));

        verify(portalService).timetable(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void attendance_idorOtherClass_returns404() throws Exception {
        when(portalService.attendance(eq(9L), isNull(), isNull())).thenThrow(
                new OracleBusinessException(StudentScopeGuard.CLASS_NOT_FOUND, "Không tìm thấy lớp học."));

        mockMvc.perform(get("/api/v1/portal/me/attendance").param("classId", "9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void attendance_ownClass_ok() throws Exception {
        when(portalService.attendance(eq(3L), any(), any())).thenReturn(List.of(
                PortalAttendanceDto.builder().id(1L).classId(3L).status("PRESENT")
                        .attendanceDate(LocalDate.of(2026, 10, 1)).build()));

        mockMvc.perform(get("/api/v1/portal/me/attendance")
                        .param("classId", "3")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PRESENT"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void attendanceSummary_ok() throws Exception {
        when(portalService.attendanceSummary(3L)).thenReturn(PortalAttendanceSummaryDto.builder()
                .classId(3L).presentCount(2).absentCount(1).lateCount(0).excusedCount(0).totalCount(3).build());

        mockMvc.perform(get("/api/v1/portal/me/attendance/summary").param("classId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presentCount").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(3));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void grades_ok() throws Exception {
        when(portalService.grades()).thenReturn(List.of(PortalGradeDto.builder()
                .id(1L).gradeType("QUIZ").score(new BigDecimal("9")).weight(BigDecimal.ONE).build()));

        mockMvc.perform(get("/api/v1/portal/me/grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].gradeType").value("QUIZ"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void fees_ok() throws Exception {
        when(portalService.fees()).thenReturn(List.of(PortalFeeListItemDto.builder()
                .id(7L).feeCode("HP7").status("UNPAID").remainingAmount(new BigDecimal("100")).build()));

        mockMvc.perform(get("/api/v1/portal/me/fees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].feeCode").value("HP7"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void feeDetail_idorOtherFee_returns404() throws Exception {
        when(portalService.feeDetail(99L)).thenThrow(
                new OracleBusinessException(StudentScopeGuard.FEE_NOT_FOUND, "Không tìm thấy khoản học phí."));

        mockMvc.perform(get("/api/v1/portal/me/fees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEE_NOT_FOUND"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void feeDetail_own_ok() throws Exception {
        when(portalService.feeDetail(7L)).thenReturn(PortalFeeDetailDto.builder()
                .id(7L).feeCode("HP7").status("UNPAID").build());

        mockMvc.perform(get("/api/v1/portal/me/fees/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeCode").value("HP7"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void feeSlipHtml_own_ok() throws Exception {
        when(portalService.feeSlipHtml(7L)).thenReturn("<html>ok</html>");

        mockMvc.perform(get("/api/v1/portal/me/fees/7/slip/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string("<html>ok</html>"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void feeSlipHtml_idor_returns404() throws Exception {
        when(portalService.feeSlipHtml(99L)).thenThrow(
                new OracleBusinessException(StudentScopeGuard.FEE_NOT_FOUND, "Không tìm thấy khoản học phí."));

        mockMvc.perform(get("/api/v1/portal/me/fees/99/slip/html"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEE_NOT_FOUND"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void payments_ok() throws Exception {
        when(portalService.payments()).thenReturn(List.of(PortalPaymentDto.builder()
                .id(1L).transactionCode("GD1").status("SUCCESS").amount(new BigDecimal("50")).build()));

        mockMvc.perform(get("/api/v1/portal/me/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].transactionCode").value("GD1"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void fees_asStaff_isStudentOnly() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/fees"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        verifyNoInteractions(portalService);
    }
}
