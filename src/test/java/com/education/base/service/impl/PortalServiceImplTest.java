package com.education.base.service.impl;

import com.education.base.dto.request.TimetableFilterRequest;
import com.education.base.dto.response.PortalAttendanceDto;
import com.education.base.dto.response.PortalDashboardResponse;
import com.education.base.dto.response.PortalFeeDetailDto;
import com.education.base.dto.response.PortalMeResponse;
import com.education.base.dto.response.PortalPaymentDto;
import com.education.base.dto.response.PortalTimetableResponse;
import com.education.base.dto.response.TimetableItemDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.entity.UserEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.GradeRepository;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.PortalAnnouncementQuery;
import com.education.base.service.TuitionSlipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalServiceImplTest {

    @Mock
    private PortalStudentContext portalStudentContext;
    @Mock
    private StudentScopeGuard studentScopeGuard;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private ClassSessionRepository classSessionRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private TuitionFeeRepository tuitionFeeRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private TuitionSlipService tuitionSlipService;
    @Mock
    private PortalAnnouncementQuery portalAnnouncementQuery;
    @InjectMocks
    private PortalServiceImpl service;

    @Test
    void me_returnsLinkedStudentProfileAndEnrolledClasses() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(studentRepository.findByIdAndIsDeleted(42L, 0)).thenReturn(Optional.of(StudentEntity.builder()
                .id(42L).studentCode("HS42").fullName("Nguyen Van A").dateOfBirth(LocalDate.of(2012, 5, 1))
                .parentName("Nguyen Van B").phone("0900000000").email("a@example.com").address("secret")
                .note("internal note").status("ACTIVE").isDeleted(0).build()));
        ClassEntity clazz = ClassEntity.builder().id(3L).classCode("L3").className("Lop 3").status("ACTIVE").build();
        when(classStudentRepository.findActiveEnrollmentsWithClass(42L)).thenReturn(List.of(
                ClassStudentEntity.builder().clazz(clazz).build(),
                ClassStudentEntity.builder().clazz(null).build()));

        PortalMeResponse me = service.me();

        assertThat(me.getStudentId()).isEqualTo(42L);
        assertThat(me.getStudentCode()).isEqualTo("HS42");
        assertThat(me.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(me.getDateOfBirth()).isEqualTo(LocalDate.of(2012, 5, 1));
        assertThat(me.getGender()).isNull();
        assertThat(me.getParentName()).isEqualTo("Nguyen Van B");
        assertThat(me.getPhone()).isEqualTo("0900000000");
        assertThat(me.getEmail()).isEqualTo("a@example.com");
        assertThat(me.getClasses()).singleElement().satisfies(c -> {
            assertThat(c.getClassId()).isEqualTo(3L);
            assertThat(c.getClassCode()).isEqualTo("L3");
            assertThat(c.getClassName()).isEqualTo("Lop 3");
            assertThat(c.getStatus()).isEqualTo("ACTIVE");
        });
        assertThat(me.toString()).doesNotContain("secret").doesNotContain("internal note");
    }

    @Test
    void me_studentRecordGone_isNotFound() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(studentRepository.findByIdAndIsDeleted(42L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me()).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("STUDENT_NOT_FOUND");
    }

    @Test
    void dashboard_aggregatesNextSessionFeesAndUnreadStub() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(classStudentRepository.findActiveEnrollmentsWithClass(42L)).thenReturn(List.of(
                ClassStudentEntity.builder().classId(3L).build()));
        ClassEntity clazz = ClassEntity.builder().id(3L).classCode("L3").className("Lop 3").build();
        when(classSessionRepository.findUpcomingScheduledSessions(anyCollection(), any(LocalDate.class)))
                .thenReturn(List.of(ClassSessionEntity.builder()
                        .id(10L).classId(3L).clazz(clazz).sessionDate(LocalDate.now().plusDays(1))
                        .startTime("08:00").endTime("09:30").roomName("P1").status("SCHEDULED")
                        .teacher(UserEntity.builder().fullName("GV A").build()).build()));
        when(tuitionFeeRepository.findByStudentIdAndIsDeleted(42L, 0)).thenReturn(List.of(
                TuitionFeeEntity.builder().id(1L).status("UNPAID").totalAmount(new BigDecimal("100"))
                        .discountAmount(BigDecimal.ZERO).paidAmount(BigDecimal.ZERO).build(),
                TuitionFeeEntity.builder().id(2L).status("PAID").totalAmount(new BigDecimal("50"))
                        .discountAmount(BigDecimal.ZERO).paidAmount(new BigDecimal("50")).build(),
                TuitionFeeEntity.builder().id(3L).status("CANCELLED").totalAmount(new BigDecimal("20"))
                        .discountAmount(BigDecimal.ZERO).paidAmount(BigDecimal.ZERO).build()));
        when(portalAnnouncementQuery.countUnreadForStudent(42L)).thenReturn(0L);

        PortalDashboardResponse dashboard = service.dashboard();

        assertThat(dashboard.getNextSession()).isNotNull();
        assertThat(dashboard.getNextSession().getSessionId()).isEqualTo(10L);
        assertThat(dashboard.getNextSession().getClassCode()).isEqualTo("L3");
        assertThat(dashboard.getOutstandingFees().getOutstandingCount()).isEqualTo(1L);
        assertThat(dashboard.getOutstandingFees().getTotalRemaining()).isEqualByComparingTo("100");
        assertThat(dashboard.getUnreadAnnouncements()).isZero();
    }

    @Test
    void myClasses_includesSubjectTeacherRoomDates() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        UserEntity teacher = UserEntity.builder().id(5L).fullName("GV A").build();
        ClassEntity clazz = ClassEntity.builder().id(3L).classCode("L3").className("Lop 3").status("ONGOING")
                .subjectName("Toan").teacherId(5L).teacher(teacher).roomName("P2")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 6, 30)).build();
        when(classStudentRepository.findActiveEnrollmentsWithClass(42L)).thenReturn(List.of(
                ClassStudentEntity.builder().classId(3L).clazz(clazz).build()));

        assertThat(service.myClasses()).singleElement().satisfies(c -> {
            assertThat(c.getSubjectName()).isEqualTo("Toan");
            assertThat(c.getTeacherName()).isEqualTo("GV A");
            assertThat(c.getRoomName()).isEqualTo("P2");
            assertThat(c.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(c.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        });
    }

    @Test
    void timetable_usesSessionStudentIdNeverClient() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(classSessionRepository.findTimetableByRange(any())).thenReturn(List.of(
                TimetableItemDto.builder().id(1L).classId(3L).classCode("L3").sessionDate(LocalDate.of(2026, 10, 6))
                        .startTime("08:00").endTime("09:00").status("SCHEDULED").note("secret staff note").build()));

        PortalTimetableResponse response = service.timetable(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        ArgumentCaptor<TimetableFilterRequest> captor = ArgumentCaptor.forClass(TimetableFilterRequest.class);
        verify(classSessionRepository).findTimetableByRange(captor.capture());
        assertThat(captor.getValue().getStudentId()).isEqualTo(42L);
        assertThat(captor.getValue().getFromDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(captor.getValue().getToDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSessionId()).isEqualTo(1L);
            assertThat(item.toString()).doesNotContain("secret staff note");
        });
    }

    @Test
    void attendance_requiresEnrollment_hidesNote() {
        when(studentScopeGuard.requireEnrolled(3L)).thenReturn(
                ClassStudentEntity.builder().classId(3L).studentId(42L).status("ENROLLED").build());
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        ClassEntity clazz = ClassEntity.builder().id(3L).classCode("L3").className("Lop 3").build();
        when(attendanceRepository.findByClassIdAndStudentIdAndIsDeleted(3L, 42L, 0)).thenReturn(List.of(
                AttendanceEntity.builder().id(1L).classId(3L).clazz(clazz)
                        .attendanceDate(LocalDate.of(2026, 10, 1)).status("PRESENT")
                        .note("internal teacher note").build()));

        List<PortalAttendanceDto> rows = service.attendance(3L, null, null);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo("PRESENT");
            assertThat(row.toString()).doesNotContain("internal teacher note");
        });
        verify(studentScopeGuard).requireEnrolled(3L);
    }

    @Test
    void attendance_otherStudentsClass_propagatesNotFound() {
        when(studentScopeGuard.requireEnrolled(9L)).thenThrow(
                new OracleBusinessException(StudentScopeGuard.CLASS_NOT_FOUND, "Không tìm thấy lớp học."));

        assertThatThrownBy(() -> service.attendance(9L, null, null))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(StudentScopeGuard.CLASS_NOT_FOUND);
        verify(attendanceRepository, never()).findByClassIdAndStudentIdAndIsDeleted(any(), any(), any());
    }

    @Test
    void grades_hidesNote_emptyOk() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(gradeRepository.findByStudentIdAndIsDeleted(42L, 0)).thenReturn(List.of(
                GradeEntity.builder().id(1L).classId(3L).gradeType("QUIZ").score(new BigDecimal("8.5"))
                        .weight(BigDecimal.ONE).examDate(LocalDate.of(2026, 9, 1))
                        .note("weak at algebra").build()));

        assertThat(service.grades()).singleElement().satisfies(g -> {
            assertThat(g.getScore()).isEqualByComparingTo("8.5");
            assertThat(g.toString()).doesNotContain("weak at algebra");
        });
    }

    @Test
    void feeDetail_idor_otherStudentsFee_isNotFound() {
        when(studentScopeGuard.requireOwnFee(7L)).thenThrow(
                new OracleBusinessException(StudentScopeGuard.FEE_NOT_FOUND, "Không tìm thấy khoản học phí."));

        assertThatThrownBy(() -> service.feeDetail(7L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(StudentScopeGuard.FEE_NOT_FOUND);
        verify(paymentTransactionRepository, never()).findByTuitionFeeIdAndIsDeleted(any(), any());
    }

    @Test
    void feeDetail_ownFee_hidesVoidInternals() {
        ClassEntity clazz = ClassEntity.builder().id(3L).classCode("L3").className("Lop 3").build();
        TuitionFeeEntity fee = TuitionFeeEntity.builder().id(7L).studentId(42L).feeCode("HP7").classId(3L)
                .clazz(clazz).totalAmount(new BigDecimal("100")).discountAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO).status("UNPAID").dueDate(LocalDate.of(2026, 10, 31)).build();
        when(studentScopeGuard.requireOwnFee(7L)).thenReturn(fee);
        when(paymentTransactionRepository.findByTuitionFeeIdAndIsDeleted(7L, 0)).thenReturn(List.of(
                PaymentTransactionEntity.builder().id(1L).tuitionFeeId(7L).transactionCode("GD1")
                        .amount(new BigDecimal("50")).paymentMethod("CASH")
                        .paymentDate(LocalDateTime.of(2026, 10, 1, 10, 0))
                        .status("VOIDED").transactionType("PAYMENT")
                        .voidedAt(LocalDateTime.of(2026, 10, 2, 9, 0)).voidedBy("admin")
                        .voidReason("internal mistake").build()));

        PortalFeeDetailDto detail = service.feeDetail(7L);

        assertThat(detail.getFeeCode()).isEqualTo("HP7");
        assertThat(detail.getPayments()).singleElement().satisfies(p -> {
            assertThat(p.getStatus()).isEqualTo("VOIDED");
            assertThat(p.toString()).doesNotContain("admin").doesNotContain("internal mistake");
        });
    }

    @Test
    void feeSlipHtml_requiresOwnFee() {
        when(studentScopeGuard.requireOwnFee(7L)).thenReturn(
                TuitionFeeEntity.builder().id(7L).studentId(42L).build());
        when(tuitionSlipService.generateSlipHtml(7L)).thenReturn("<html>slip</html>");

        assertThat(service.feeSlipHtml(7L)).isEqualTo("<html>slip</html>");
        verify(studentScopeGuard).requireOwnFee(7L);
    }

    @Test
    void payments_onlyOwnFees_hidesVoidDetails() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        TuitionFeeEntity fee = TuitionFeeEntity.builder().id(7L).studentId(42L).feeCode("HP7").build();
        when(tuitionFeeRepository.findByStudentIdAndIsDeleted(42L, 0)).thenReturn(List.of(fee));
        when(paymentTransactionRepository.findByTuitionFeeIdAndIsDeleted(7L, 0)).thenReturn(List.of(
                PaymentTransactionEntity.builder().id(1L).tuitionFeeId(7L).transactionCode("GD1")
                        .amount(new BigDecimal("50")).paymentMethod("CASH")
                        .paymentDate(LocalDateTime.of(2026, 10, 1, 10, 0))
                        .status("VOIDED").transactionType("PAYMENT")
                        .voidedBy("cashier").voidReason("typo").build()));

        List<PortalPaymentDto> payments = service.payments();

        assertThat(payments).singleElement().satisfies(p -> {
            assertThat(p.getFeeCode()).isEqualTo("HP7");
            assertThat(p.getStatus()).isEqualTo("VOIDED");
            assertThat(p.toString()).doesNotContain("cashier").doesNotContain("typo");
        });
    }
}
