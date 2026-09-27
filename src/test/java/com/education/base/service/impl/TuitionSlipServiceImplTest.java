package com.education.base.service.impl;

import com.education.base.common.VietQrHelper;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.request.CreateMonthlyInvoiceRequestDto;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.BankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TuitionSlipServiceImplTest {

    @Mock
    private TuitionFeeRepository tuitionFeeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private BankAccountService bankAccountService;

    private TuitionSlipServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TuitionSlipServiceImpl(tuitionFeeRepository, attendanceRepository, classStudentRepository,
                classRepository, studentRepository, bankAccountService);
    }

    @Test
    void generateMonthly_countsOnlyPresentAndMultipliesAmount() {
        when(classRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(3L).classCode("LH01").className("Lớp học Sao Mai").build()));
        when(classStudentRepository.findByClassIdAndIsDeleted(3L, 0)).thenReturn(List.of(
                ClassStudentEntity.builder().classId(3L).studentId(8L).status("ENROLLED").isDeleted(0).build()));
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(StudentEntity.builder()
                .id(8L).studentCode("SV20260001").fullName("Nguyen Van A").status("ACTIVE").isDeleted(0).build()));
        when(attendanceRepository.findByClassIdAndAttendanceDateBetweenAndIsDeleted(
                eq(3L), any(), any(), eq(0))).thenReturn(List.of(
                AttendanceEntity.builder().studentId(8L).status("PRESENT")
                        .attendanceDate(LocalDate.of(2026, 4, 2)).build(),
                AttendanceEntity.builder().studentId(8L).status("ABSENT")
                        .attendanceDate(LocalDate.of(2026, 4, 4)).build(),
                AttendanceEntity.builder().studentId(8L).status("LATE")
                        .attendanceDate(LocalDate.of(2026, 4, 6)).build(),
                AttendanceEntity.builder().studentId(8L).status("PRESENT")
                        .attendanceDate(LocalDate.of(2026, 4, 8)).build()));
        when(tuitionFeeRepository.findByStudentIdAndClassIdAndFeeYearAndFeeMonthAndIsDeleted(
                8L, 3L, 2026, 4, 0)).thenReturn(Optional.empty());
        when(tuitionFeeRepository.nextTuitionFeeCode()).thenReturn("HP2026040001");
        when(bankAccountService.requireActive()).thenReturn(BankAccountResponseDto.builder()
                .bankBin("970436")
                .bankName("Vietcombank")
                .accountNo("1234567890")
                .accountName("TRUONG EDUCATION")
                .active(true)
                .build());
        when(tuitionFeeRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TuitionFeeEntity entity = invocation.getArgument(0);
            entity.setId(91L);
            return entity;
        });

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(CreateMonthlyInvoiceRequestDto.builder()
                .classId(3L)
                .month(4)
                .year(2026)
                .pricePerSession(new BigDecimal("80000"))
                .build());

        ArgumentCaptor<TuitionFeeEntity> captor = ArgumentCaptor.forClass(TuitionFeeEntity.class);
        org.mockito.Mockito.verify(tuitionFeeRepository).saveAndFlush(captor.capture());
        TuitionFeeEntity saved = captor.getValue();
        assertThat(saved.getTotalSessions()).isEqualTo(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("160000.00");
        assertThat(saved.getPricePerSession()).isEqualByComparingTo("80000.00");
        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getSlips().getFirst().getAttendedDates()).containsExactly("02/04", "08/04");
        assertThat(result.getSlips().getFirst().getQrPayload()).contains("5406160000");
        assertThat(result.getSlips().getFirst().getQrPayload()).contains("SV20260001 HP T4");
        assertThat(result.getSlips().getFirst().getQrBase64()).startsWith("data:image/png;base64,");
    }

    @Test
    void transferContent_isAsciiAndShort() {
        assertThat(TuitionSlipServiceImpl.transferContent("SV20260001", 4)).isEqualTo("SV20260001 HP T4");
        assertThat(TuitionSlipServiceImpl.transferContent("Sinh Viên", 12)).isEqualTo("SinhVien HP T12");
        assertThat(TuitionSlipServiceImpl.transferContent("THISCODEISTOOLONGFORNAPASXX", 4).length())
                .isLessThanOrEqualTo(25);
    }

    @Test
    void generateSlipHtml_containsEmeraldCardAndPresentDates() {
        String html = service.generateSlipHtml(TuitionSlipResponseDto.builder()
                .invoiceCode("HP2026040001")
                .monthYearText("Tháng 04/2026")
                .className("Lớp học Sao Mai")
                .studentName("Nguyễn Văn A")
                .studentCode("SV01")
                .slipLabel("Mặc Định")
                .pricePerSession(new BigDecimal("80000"))
                .totalSessions(12)
                .totalAmount(new BigDecimal("960000"))
                .attendedDates(List.of("02/04", "04/04"))
                .teacherComment("Bé ngoan")
                .footerWish("Chúc em học tốt!")
                .bankName("Vietcombank")
                .accountNo("1234567890")
                .accountName("TRUONG EDUCATION")
                .qrBase64("data:image/png;base64,abc")
                .build());

        assertThat(html).contains("PHIẾU HỌC PHÍ");
        assertThat(html).contains("Lớp học Sao Mai");
        assertThat(html).contains("Mặc Định");
        assertThat(html).contains("960.000đ");
        assertThat(html).contains("80.000đ");
        assertThat(html).contains("02/04");
        assertThat(html).contains("#0f766e");
        assertThat(html).contains("dashed");
        assertThat(html).doesNotContain("<script>");
    }

    @Test
    void htmlEscapesCommentToAvoidXss() {
        String html = service.generateSlipHtml(TuitionSlipResponseDto.builder()
                .studentName("<script>alert(1)</script>")
                .teacherComment("<img src=x onerror=alert(1)>")
                .totalAmount(BigDecimal.ZERO)
                .attendedDates(List.of())
                .build());
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void formatMonthYear_padsMonth() {
        assertThat(TuitionSlipServiceImpl.formatMonthYear(4, 2026)).isEqualTo("Tháng 04/2026");
    }

    @Test
    void totalAmount_matchesSessionsTimesPrice() {
        BigDecimal price = new BigDecimal("80000");
        int sessions = 12;
        assertThat(price.multiply(BigDecimal.valueOf(sessions))).isEqualByComparingTo("960000");
        String payload = VietQrHelper.buildVietQrPayload("970436", "1234567890", 960000L, "SV01 HP T4");
        assertThat(payload).contains("5406960000");
        assertThat(payload).startsWith("000201010212");
        assertThat(payload).contains("SV01 HP T4");
    }
}
