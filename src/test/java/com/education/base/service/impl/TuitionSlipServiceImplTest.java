package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.request.CreateMonthlyInvoiceRequestDto;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentDiscountEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentDiscountRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.BankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TuitionSlipServiceImplTest {

    private static final long CLASS_ID = 3L;
    /** Tháng 4/2026 đã qua so với ngày chạy test (>= 10/2026) trừ khi chỉ định khác. */
    private static final int MONTH = 4;
    private static final int YEAR = 2026;

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
    @Mock
    private StudentDiscountRepository studentDiscountRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private TuitionSlipServiceImpl service;

    @BeforeEach
    void setUp() {
        StudentDiscountServiceImpl discountService = new StudentDiscountServiceImpl(
                studentDiscountRepository, studentRepository, classRepository, null);
        service = new TuitionSlipServiceImpl(tuitionFeeRepository, attendanceRepository, classStudentRepository,
                classRepository, studentRepository, bankAccountService, discountService, transactionManager);
        lenient().when(bankAccountService.requireActive()).thenReturn(BankAccountResponseDto.builder()
                .bankBin("970436")
                .bankName("Vietcombank")
                .accountNo("1234567890")
                .accountName("TRUONG EDUCATION")
                .active(true)
                .build());
        lenient().when(tuitionFeeRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TuitionFeeEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(91L);
            }
            return entity;
        });
    }

    // ---- B1 -------------------------------------------------------------------------------------------

    @Test
    void generateMonthly_billsPresentAndLateButNotAbsent() {
        givenClassWithStudents(student(8L, "SV20260001"));
        givenAttendance(
                attendance(8L, "PRESENT", 2),
                attendance(8L, "ABSENT", 4),
                attendance(8L, "LATE", 6),
                attendance(8L, "EXCUSED", 7),
                attendance(8L, "PRESENT", 8));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.empty());
        when(tuitionFeeRepository.nextTuitionFeeCode()).thenReturn("HP2026040001");

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        TuitionFeeEntity saved = capturedSave();
        assertThat(saved.getTotalSessions()).isEqualTo(3);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("240000.00");
        assertThat(saved.getPricePerSession()).isEqualByComparingTo("80000.00");
        assertThat(result.getCreatedCount()).isEqualTo(1);
        TuitionSlipResponseDto slip = result.getSlips().getFirst();
        assertThat(slip.getAttendedDates()).containsExactly("02/04", "06/04", "08/04");
        assertThat(slip.getQrPayload()).contains("5406240000");
        assertThat(slip.getQrPayload()).contains("SV20260001 HP T4");
        assertThat(slip.getQrBase64()).startsWith("data:image/png;base64,");
        assertThat(DomainConstants.BILLABLE_ATTENDANCE_STATUSES).containsExactly("PRESENT", "LATE");
    }

    // ---- B2 -------------------------------------------------------------------------------------------

    @Test
    void generateMonthly_existingFee_isReloadedUnderRowLockAndPaidAmountPreserved() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance(attendance(8L, "PRESENT", 2), attendance(8L, "PRESENT", 9), attendance(8L, "LATE", 16));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.of(50L));
        // Bản ghi dưới khóa dòng có PAID_AMOUNT mới nhất (một thanh toán vừa được ghi đồng thời).
        LocalDate due = LocalDate.now().plusDays(10);
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(50L, 0)).thenReturn(Optional.of(fee(50L, 8L)
                .totalAmount(new BigDecimal("160000")).paidAmount(new BigDecimal("100000"))
                .dueDate(due).status("PARTIAL").build()));

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        TuitionFeeEntity saved = capturedSave();
        assertThat(saved.getId()).isEqualTo(50L);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo("100000");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("240000.00");
        assertThat(saved.getStatus()).isEqualTo("PARTIAL");
        assertThat(saved.getDueDate()).isEqualTo(due);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getSlips().getFirst().getRemainingAmount()).isEqualByComparingTo("140000");
        assertThat(result.getSlips().getFirst().getQrPayload()).contains("5406140000");
        verify(tuitionFeeRepository, never()).findByStudentIdAndClassIdAndFeeYearAndFeeMonthAndIsDeleted(
                any(), any(), any(), any(), any());
    }

    // ---- B3 -------------------------------------------------------------------------------------------

    @Test
    void generateMonthly_netBelowPaid_isReportedAsConflictAndFeeLeftUnchanged() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance(attendance(8L, "PRESENT", 2));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.of(50L));
        TuitionFeeEntity existing = fee(50L, 8L).feeCode("HP01")
                .totalAmount(new BigDecimal("240000")).paidAmount(new BigDecimal("200000")).status("PARTIAL").build();
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(50L, 0)).thenReturn(Optional.of(existing));

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        verify(tuitionFeeRepository, never()).saveAndFlush(any());
        assertThat(existing.getTotalAmount()).isEqualByComparingTo("240000");
        assertThat(existing.getStatus()).isEqualTo("PARTIAL");
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getSlips()).isEmpty();
        assertThat(result.getConflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.getFeeId()).isEqualTo(50L);
            assertThat(conflict.getReason()).isEqualTo(GenerateMonthlyInvoicesResponseDto.CONFLICT_NET_BELOW_PAID);
            assertThat(conflict.getNewTotalAmount()).isEqualByComparingTo("80000");
            assertThat(conflict.getPaidAmount()).isEqualByComparingTo("200000");
        });
    }

    @Test
    void generateMonthly_discountAboveNewTotal_isCappedAtTotal() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance(attendance(8L, "PRESENT", 2));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.of(50L));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(50L, 0)).thenReturn(Optional.of(fee(50L, 8L)
                .totalAmount(new BigDecimal("240000")).discountAmount(new BigDecimal("150000"))
                .paidAmount(BigDecimal.ZERO).status("UNPAID").build()));

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        TuitionFeeEntity saved = capturedSave();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("80000");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("80000");
        assertThat(saved.getDiscountAmount()).isLessThanOrEqualTo(saved.getTotalAmount());
        assertThat(saved.getStatus()).isEqualTo("PAID");
        assertThat(result.getSlips().getFirst().getQrPayload()).isNull();
    }

    @Test
    void generateMonthly_oneFailingStudent_doesNotRollBackTheOthers() {
        givenClassWithStudents(student(8L, "SV01"), student(9L, "SV02"));
        givenAttendance(attendance(8L, "PRESENT", 2), attendance(9L, "PRESENT", 2));
        when(tuitionFeeRepository.findMonthlyFeeId(anyLong(), eq(CLASS_ID), eq(YEAR), eq(MONTH)))
                .thenReturn(Optional.empty());
        when(tuitionFeeRepository.nextTuitionFeeCode()).thenReturn("HP01", "HP02");
        doAnswer(invocation -> {
            TuitionFeeEntity entity = invocation.getArgument(0);
            if (entity.getStudentId() == 8L) {
                throw new DataIntegrityViolationException("ORA-02290: check constraint (CK_FEES_DISCOUNT) violated");
            }
            entity.setId(92L);
            return entity;
        }).when(tuitionFeeRepository).saveAndFlush(any());

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getSlips()).extracting(TuitionSlipResponseDto::getInvoiceId).containsExactly(92L);
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getStudentId()).isEqualTo(8L);
            assertThat(error.getCode()).isEqualTo("FEE_GENERATE_FAILED");
        });
        // mỗi học sinh một transaction: một rollback (SV01), một commit (SV02)
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(any());
        verify(transactionManager, times(1)).commit(any());
    }

    // ---- B4 -------------------------------------------------------------------------------------------

    @Test
    void generateMonthly_noBillableSessions_unpaidExistingFeeIsAutoCancelled() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance(attendance(8L, "ABSENT", 2));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.of(50L));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(50L, 0)).thenReturn(Optional.of(fee(50L, 8L)
                .totalAmount(new BigDecimal("160000")).paidAmount(BigDecimal.ZERO).status("UNPAID").build()));

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        TuitionFeeEntity saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo("CANCELLED");
        assertThat(saved.getNote()).isEqualTo("auto: không còn buổi tính phí");
        assertThat(saved.getCancelReason()).isEqualTo("auto: không còn buổi tính phí");
        assertThat(result.getCancelledCount()).isEqualTo(1);
        assertThat(result.getSkippedNoAttendance()).isZero();
        assertThat(result.getSlips()).isEmpty();
    }

    @Test
    void generateMonthly_noBillableSessions_paidExistingFeeIsConflict() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance();
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.of(50L));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(50L, 0)).thenReturn(Optional.of(fee(50L, 8L)
                .totalAmount(new BigDecimal("160000")).paidAmount(new BigDecimal("50000")).status("PARTIAL").build()));

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        verify(tuitionFeeRepository, never()).saveAndFlush(any());
        assertThat(result.getConflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.getFeeId()).isEqualTo(50L);
            assertThat(conflict.getReason())
                    .isEqualTo(GenerateMonthlyInvoicesResponseDto.CONFLICT_NO_SESSIONS_HAS_PAYMENTS);
        });
    }

    @Test
    void generateMonthly_noBillableSessionsAndNoFee_isSkipped() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance();
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.empty());

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        verify(tuitionFeeRepository, never()).saveAndFlush(any());
        assertThat(result.getSkippedNoAttendance()).isEqualTo(1);
    }

    @Test
    void generateMonthly_autoCancelledFeeWithSessionsAgain_isReopened() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance(attendance(8L, "PRESENT", 2));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.of(50L));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(50L, 0)).thenReturn(Optional.of(fee(50L, 8L)
                .totalAmount(new BigDecimal("160000")).paidAmount(BigDecimal.ZERO).status("CANCELLED")
                .cancelReason(DomainConstants.FEE_AUTO_CANCEL_NO_SESSION_NOTE).build()));

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        TuitionFeeEntity saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo("UNPAID");
        assertThat(saved.getCancelReason()).isNull();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("80000");
        assertThat(result.getUpdatedCount()).isEqualTo(1);
    }

    @Test
    void generateMonthly_manuallyCancelledFee_isLeftUnchanged() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance(attendance(8L, "PRESENT", 2));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.of(50L));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(50L, 0)).thenReturn(Optional.of(fee(50L, 8L)
                .totalAmount(new BigDecimal("160000")).paidAmount(BigDecimal.ZERO).status("CANCELLED")
                .cancelReason("Học sinh chuyển lớp").build()));

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        verify(tuitionFeeRepository, never()).saveAndFlush(any());
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getSlips()).singleElement()
                .satisfies(slip -> assertThat(slip.getQrPayload()).isNull());
    }

    // ---- B5 -------------------------------------------------------------------------------------------

    @Test
    void resolveDueDate_pastMonthGetsGracePeriod_currentMonthKeepsEndOfMonth() {
        LocalDate today = LocalDate.of(2026, 10, 2);
        assertThat(TuitionSlipServiceImpl.resolveDueDate(LocalDate.of(2026, 4, 30), today))
                .isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(TuitionSlipServiceImpl.resolveDueDate(LocalDate.of(2026, 10, 31), today))
                .isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(TuitionSlipServiceImpl.resolveDueDate(today, today)).isEqualTo(today);
    }

    @Test
    void generateMonthly_backBillingPastMonth_isNotOverdueImmediately() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance(attendance(8L, "PRESENT", 2));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, 2020, 1)).thenReturn(Optional.empty());
        when(tuitionFeeRepository.nextTuitionFeeCode()).thenReturn("HP2020010001");

        service.generateMonthly(request(1, 2020));

        TuitionFeeEntity saved = capturedSave();
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.now().plusDays(DomainConstants.FEE_BACKBILL_GRACE_DAYS));
        assertThat(saved.getStatus()).isEqualTo("UNPAID");
    }

    // ---- Miễn giảm --------------------------------------------------------------------------------------

    @Test
    void generateMonthly_appliesActiveDiscountsToDiscountAmount() {
        givenClassWithStudents(student(8L, "SV01"));
        givenAttendance(attendance(8L, "PRESENT", 2), attendance(8L, "PRESENT", 9));
        when(tuitionFeeRepository.findMonthlyFeeId(8L, CLASS_ID, YEAR, MONTH)).thenReturn(Optional.empty());
        when(tuitionFeeRepository.nextTuitionFeeCode()).thenReturn("HP01");
        when(studentDiscountRepository.findApplicable(List.of(8L), CLASS_ID,
                LocalDate.of(YEAR, MONTH, 1), LocalDate.of(YEAR, MONTH, 30))).thenReturn(List.of(
                StudentDiscountEntity.builder().id(1L).studentId(8L).discountType("PERCENT")
                        .discountValue(new BigDecimal("10")).build(),
                StudentDiscountEntity.builder().id(2L).studentId(8L).discountType("AMOUNT")
                        .discountValue(new BigDecimal("20000")).build()));

        GenerateMonthlyInvoicesResponseDto result = service.generateMonthly(request(MONTH, YEAR));

        TuitionFeeEntity saved = capturedSave();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("160000");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("36000");
        assertThat(result.getSlips().getFirst().getRemainingAmount()).isEqualByComparingTo("124000");
        assertThat(result.getSlips().getFirst().getQrPayload()).contains("5406124000");
    }

    @Test
    void generateMonthly_withoutBankAccount_writesNothing() {
        when(classRepository.findByIdAndIsDeleted(CLASS_ID, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(CLASS_ID).classCode("LH01").className("Lớp học Sao Mai").build()));
        when(bankAccountService.requireActive()).thenThrow(new com.education.base.exception.OracleBusinessException(
                "BANK_ACCOUNT_NOT_CONFIGURED", "Chưa có STK"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateMonthly(request(MONTH, YEAR)))
                .hasMessageContaining("STK");
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
    }

    // ---- B6 -------------------------------------------------------------------------------------------

    @Test
    void getSlip_partiallyPaid_qrUsesRemainingAndSlipCarriesAmounts() {
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee(9L, 8L)
                .totalAmount(new BigDecimal("960000")).discountAmount(new BigDecimal("60000"))
                .paidAmount(new BigDecimal("300000")).status("PARTIAL").build()));
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(slipData(9L));

        TuitionSlipResponseDto slip = service.getSlip(9L);

        assertThat(slip.getDiscountAmount()).isEqualByComparingTo("60000");
        assertThat(slip.getPaidAmount()).isEqualByComparingTo("300000");
        assertThat(slip.getRemainingAmount()).isEqualByComparingTo("600000");
        assertThat(slip.getStatus()).isEqualTo("PARTIAL");
        assertThat(slip.getQrPayload()).contains("5406600000").doesNotContain("5406960000");
        assertThat(slip.getQrBase64()).startsWith("data:image/png;base64,");
    }

    @Test
    void getSlip_paidOrCancelledFee_hasNoQr() {
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee(9L, 8L)
                .totalAmount(new BigDecimal("960000")).paidAmount(new BigDecimal("960000")).status("PAID").build()));
        when(tuitionFeeRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(fee(10L, 8L)
                .totalAmount(new BigDecimal("960000")).paidAmount(BigDecimal.ZERO).status("CANCELLED").build()));
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(slipData(9L));
        when(tuitionFeeRepository.getTuitionSlipData(10L)).thenReturn(slipData(10L));

        TuitionSlipResponseDto paid = service.getSlip(9L);
        TuitionSlipResponseDto cancelled = service.getSlip(10L);

        assertThat(paid.getRemainingAmount()).isEqualByComparingTo("0");
        assertThat(paid.getQrPayload()).isNull();
        assertThat(paid.getQrBase64()).isNull();
        assertThat(paid.getQuickPayUrl()).isNull();
        assertThat(cancelled.getRemainingAmount()).isEqualByComparingTo("960000");
        assertThat(cancelled.getQrPayload()).isNull();
        assertThat(cancelled.getQrBase64()).isNull();
        assertThat(service.generateSlipHtml(paid)).contains("Đã thanh toán đủ").doesNotContain("<img alt=\"VietQR\"");
        assertThat(service.generateSlipHtml(cancelled)).contains("Khoản học phí đã hủy");
    }

    @Test
    void isQrPayable_requiresPositiveRemainingAndOpenStatus() {
        assertThat(TuitionSlipServiceImpl.isQrPayable("UNPAID", 1)).isTrue();
        assertThat(TuitionSlipServiceImpl.isQrPayable("OVERDUE", 100)).isTrue();
        assertThat(TuitionSlipServiceImpl.isQrPayable("PARTIAL", 0)).isFalse();
        assertThat(TuitionSlipServiceImpl.isQrPayable("PAID", 100)).isFalse();
        assertThat(TuitionSlipServiceImpl.isQrPayable("CANCELLED", 100)).isFalse();
    }

    @Test
    void generateSlipHtml_showsDiscountPaidRemainingAndStatus() {
        String html = service.generateSlipHtml(TuitionSlipResponseDto.builder()
                .studentName("Nguyễn Văn A")
                .totalAmount(new BigDecimal("960000"))
                .discountAmount(new BigDecimal("60000"))
                .paidAmount(new BigDecimal("300000"))
                .remainingAmount(new BigDecimal("600000"))
                .status("PARTIAL")
                .attendedDates(List.of("02/04"))
                .qrBase64("data:image/png;base64,abc")
                .build());

        assertThat(html).contains("Miễn giảm").contains("-60.000đ");
        assertThat(html).contains("Đã thu").contains("300.000đ");
        assertThat(html).contains("Còn phải đóng").contains("600.000đ");
        assertThat(html).contains("Đóng một phần");
        assertThat(html).contains("<img alt=\"VietQR\" src=\"data:image/png;base64,abc\"/>");
    }

    // ---- Không đổi ------------------------------------------------------------------------------------

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
                .status("<b>")
                .totalAmount(BigDecimal.ZERO)
                .attendedDates(List.of())
                .build());
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).doesNotContain("<b>");
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

    // ---- helpers --------------------------------------------------------------------------------------

    private void givenClassWithStudents(StudentEntity... students) {
        when(classRepository.findByIdAndIsDeleted(CLASS_ID, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(CLASS_ID).classCode("LH01").className("Lớp học Sao Mai").build()));
        List<ClassStudentEntity> enrollments = new ArrayList<>();
        for (StudentEntity student : students) {
            enrollments.add(ClassStudentEntity.builder().classId(CLASS_ID).studentId(student.getId())
                    .status("ENROLLED").isDeleted(0).build());
            when(studentRepository.findByIdAndIsDeleted(student.getId(), 0)).thenReturn(Optional.of(student));
        }
        when(classStudentRepository.findByClassIdAndIsDeleted(CLASS_ID, 0)).thenReturn(enrollments);
    }

    private void givenAttendance(AttendanceEntity... rows) {
        when(attendanceRepository.findByClassIdAndAttendanceDateBetweenAndIsDeleted(
                eq(CLASS_ID), any(), any(), eq(0))).thenReturn(List.of(rows));
    }

    private TuitionFeeEntity capturedSave() {
        ArgumentCaptor<TuitionFeeEntity> captor = ArgumentCaptor.forClass(TuitionFeeEntity.class);
        verify(tuitionFeeRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static StudentEntity student(long id, String code) {
        return StudentEntity.builder().id(id).studentCode(code).fullName("Học sinh " + code)
                .status("ACTIVE").isDeleted(0).build();
    }

    private static AttendanceEntity attendance(long studentId, String status, int day) {
        return AttendanceEntity.builder().studentId(studentId).status(status)
                .attendanceDate(LocalDate.of(YEAR, MONTH, day)).build();
    }

    private static TuitionFeeEntity.TuitionFeeEntityBuilder fee(long id, long studentId) {
        return TuitionFeeEntity.builder()
                .id(id)
                .feeCode("HP" + id)
                .studentId(studentId)
                .classId(CLASS_ID)
                .feeMonth(MONTH)
                .feeYear(YEAR)
                .discountAmount(BigDecimal.ZERO)
                .isDeleted(0);
    }

    private static TuitionSlipResponseDto slipData(long id) {
        return TuitionSlipResponseDto.builder()
                .invoiceId(id)
                .invoiceCode("HP" + id)
                .month(MONTH)
                .year(YEAR)
                .studentCode("SV01")
                .studentName("Nguyễn Văn A")
                .totalSessions(12)
                .totalAmount(new BigDecimal("960000"))
                .attendedDates(new ArrayList<>(List.of("02/04")))
                .build();
    }

    private static CreateMonthlyInvoiceRequestDto request(int month, int year) {
        return CreateMonthlyInvoiceRequestDto.builder()
                .classId(CLASS_ID)
                .month(month)
                .year(year)
                .pricePerSession(new BigDecimal("80000"))
                .build();
    }
}
