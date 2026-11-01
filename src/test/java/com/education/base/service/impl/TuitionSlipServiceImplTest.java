package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FeePeriods;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.request.GenerateRangeInvoicesRequestDto;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.BillableSessionsPreviewDto;
import com.education.base.dto.response.FeeStudentErrorDto;
import com.education.base.dto.response.GenerateInvoicesResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.entity.TuitionFeeSessionEntity;
import com.education.base.exception.BusinessDataException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.repository.TuitionFeeSessionRepository;
import com.education.base.repository.projection.BillableAttendanceRow;
import com.education.base.repository.projection.FeeSessionConflictRow;
import com.education.base.service.BankAccountService;
import com.education.base.service.TuitionSlipService;
import com.education.base.testsupport.InMemoryTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thu học phí theo khoảng ngày (SPEC-fee-date-range mục 8, contract fee-range-api v1.4 §13 gói 10 buổi).
 */
@ExtendWith(MockitoExtension.class)
class TuitionSlipServiceImplTest {

    static final long CLASS_ID = 3L;
    static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

    @Mock
    TuitionFeeRepository tuitionFeeRepository;
    @Mock
    TuitionFeeSessionRepository tuitionFeeSessionRepository;
    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    ClassStudentRepository classStudentRepository;
    @Mock
    ClassRepository classRepository;
    @Mock
    StudentRepository studentRepository;
    @Mock
    BankAccountService bankAccountService;

    TuitionSlipServiceImpl service;
    final AtomicLong ids = new AtomicLong(900);

    @BeforeEach
    void setUp() {
        service = new TuitionSlipServiceImpl(tuitionFeeRepository, tuitionFeeSessionRepository, attendanceRepository,
                classStudentRepository, classRepository, studentRepository, bankAccountService);
        lenient().when(bankAccountService.requireActive()).thenReturn(BankAccountResponseDto.builder()
                .bankBin("970436").bankName("Vietcombank").accountNo("1234567890").accountName("TRUNG TAM")
                .active(true).build());
        lenient().when(tuitionFeeRepository.saveAndFlush(any())).thenAnswer(inv -> {
            TuitionFeeEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(ids.incrementAndGet());
            }
            return e;
        });
        lenient().when(tuitionFeeRepository.nextTuitionFeeCode()).thenAnswer(inv -> "HP" + ids.get());
        lenient().when(tuitionFeeSessionRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(classRepository.findByIdAndIsDeleted(CLASS_ID, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(CLASS_ID).classCode("LH01").className("Lớp Sao Mai").build()));
    }

    // ---- 1. Gói 10 buổi: PRESENT + LATE, cả hai đầu, 10 buổi cũ nhất, phần dư chờ lần sau (v1.4 §13) ----------

    @Test
    void generate_queriesBillableStatusesWithInclusiveRange_billsEarliest10_leftoversWait() {
        enrolled(student(8L, "HS01"));
        List<BillableAttendanceRow> rows = new ArrayList<>(daily(100, 8L, OCT_1, 12));
        java.util.Collections.reverse(rows);
        attendance(rows.toArray(BillableAttendanceRow[]::new));
        noConflicts();

        GenerateInvoicesResponseDto result = service.generateRange(withStudents(request(OCT_1, OCT_31, "80000"),
                sel(8L)));

        verify(attendanceRepository).findBillableInClassRange(CLASS_ID, OCT_1, OCT_31,
                DomainConstants.BILLABLE_ATTENDANCE_STATUSES);
        assertThat(DomainConstants.BILLABLE_ATTENDANCE_STATUSES).containsExactly("PRESENT", "LATE");
        assertThat(DomainConstants.FEE_PACKAGE_SIZE).isEqualTo(10);
        TuitionFeeEntity fee = savedFees().getFirst();
        assertThat(fee.getTotalSessions()).isEqualTo(10);
        assertThat(savedSessions()).extracting(TuitionFeeSessionEntity::getAttendanceId)
                .containsExactly(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L);
        assertThat(fee.getPeriodFrom()).as("PERIOD_FROM = buổi đầu thực thu").isEqualTo(OCT_1);
        assertThat(fee.getPeriodTo()).as("PERIOD_TO = buổi cuối thực thu").isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(fee.getNote()).isEqualTo("HP 01/10/2026-10/10/2026");
        assertThat(fee.getIsPartial()).isZero();
        assertThat(fee.getPartialReason()).isNull();
        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getFeeIds()).containsExactly(fee.getId());
        GenerateInvoicesResponseDto.CreatedSlip slip = result.getSlips().getFirst();
        assertThat(slip.getFeeId()).isEqualTo(fee.getId());
        assertThat(slip.getStudentId()).isEqualTo(8L);
        assertThat(slip.getSessionCount()).isEqualTo(10);
        assertThat(slip.getPeriodFrom()).isEqualTo(OCT_1);
        assertThat(slip.getPeriodTo()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(slip.isPartial()).isFalse();
        assertThat(slip.getPartialReason()).isNull();
    }

    // ---- 2. Tiền = số buổi x đơn giá, scale 2, không giảm giá ------------------------------------------

    @Test
    void generate_amountIsSessionsTimesPrice_discountZero_monthNull_dueFromLastSession() {
        enrolled(student(8L, "HS01"));
        attendance(daily(1, 8L, OCT_1, 10).toArray(BillableAttendanceRow[]::new));
        noConflicts();

        service.generateRange(withStudents(request(OCT_1, OCT_31, "80000.5"), sel(8L)));

        TuitionFeeEntity fee = savedFees().getFirst();
        assertThat(fee.getTotalAmount()).isEqualByComparingTo("800005.00");
        assertThat(fee.getTotalAmount().scale()).isEqualTo(2);
        assertThat(fee.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(fee.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(fee.getPricePerSession()).isEqualByComparingTo("80000.50");
        assertThat(fee.getFeeMonth()).isNull();
        assertThat(fee.getFeeYear()).isNull();
        assertThat(fee.getIsRange()).as("chỉ generate-range ghi IS_RANGE = 1").isEqualTo(1);
        assertThat(fee.getSlipLabel()).isEqualTo(DomainConstants.TUITION_SLIP_LABEL_DEFAULT);
        assertThat(fee.getStatus()).isIn("UNPAID", "OVERDUE");
        assertThat(fee.getDueDate()).isEqualTo(TuitionSlipServiceImpl.resolveDueDate(
                LocalDate.of(2026, 10, 10), LocalDate.now()));
    }

    // ---- 3. Chỉ buổi chưa thu; khoảng chồng nhau không bị chặn (§13.1, §13.4) ---------------------------

    @Test
    void generate_overlappingRange_skipsSessionsOnActiveSlips_noConflictError() {
        enrolled(student(8L, "HS01"));
        attendance(daily(1, 8L, OCT_1, 12).toArray(BillableAttendanceRow[]::new));
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), eq(OCT_1), eq(OCT_31), any()))
                .thenReturn(List.of(new FeeSessionConflictRow(1L, 8L, OCT_1, 50L, "HP-000812"),
                        new FeeSessionConflictRow(2L, 8L, LocalDate.of(2026, 10, 2), 50L, "HP-000812")));

        GenerateInvoicesResponseDto result = service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"),
                sel(8L)));

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(savedSessions()).extracting(TuitionFeeSessionEntity::getAttendanceId)
                .containsExactly(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
        TuitionFeeEntity fee = savedFees().getFirst();
        assertThat(fee.getPeriodFrom()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(fee.getPeriodTo()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void generate_billedSessionsLeaveFewerThan10_studentNotEligible_noWrites() {
        enrolled(student(8L, "HS01"));
        attendance(daily(1, 8L, OCT_1, 10).toArray(BillableAttendanceRow[]::new));
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), any(), any(), any()))
                .thenReturn(List.of(new FeeSessionConflictRow(1L, 8L, OCT_1, 50L, "HP-1")));

        assertThatThrownBy(() -> service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"), sel(8L))))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DomainConstants.STUDENT_NOT_ELIGIBLE);
                    FeeStudentErrorDto d = details(e).getFirst();
                    assertThat(d.getStudentId()).isEqualTo(8L);
                    assertThat(d.getStudentName()).isEqualTo("Học sinh HS01");
                    assertThat(d.getField()).isNull();
                    assertThat(d.getMessage()).contains("10 buổi");
                    assertThat(d.getUnbilledCount()).isEqualTo(9);
                    assertThat(d.getCode()).isEqualTo(TuitionSlipServiceImpl.CODE_STUDENT_NOT_ELIGIBLE);
                });
        assertNoWrites();
    }

    // ---- 4. Thu chưa đủ gói (§13.2, §13.5) ------------------------------------------------------------

    @Test
    void generate_partialWithReason_takesAllSessions_isPartial1_reasonTrimmed() {
        enrolled(student(8L, "HS01"));
        attendance(row(1, 8L, OCT_1), row(2, 8L, LocalDate.of(2026, 10, 8)), row(3, 8L, LocalDate.of(2026, 10, 15)));
        noConflicts();

        GenerateInvoicesResponseDto result = service.generateRange(withStudents(request(OCT_1, OCT_31, "80000"),
                partialSel(8L, "  Nghỉ giữa khóa  ")));

        TuitionFeeEntity fee = savedFees().getFirst();
        assertThat(fee.getTotalSessions()).isEqualTo(3);
        assertThat(fee.getTotalAmount()).isEqualByComparingTo("240000");
        assertThat(fee.getIsPartial()).isEqualTo(1);
        assertThat(fee.getPartialReason()).isEqualTo("Nghỉ giữa khóa");
        assertThat(fee.getPeriodFrom()).isEqualTo(OCT_1);
        assertThat(fee.getPeriodTo()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(result.getSlips().getFirst().isPartial()).isTrue();
        assertThat(result.getSlips().getFirst().getPartialReason()).isEqualTo("Nghỉ giữa khóa");
    }

    @Test
    void generate_allowPartialIgnoredWhenPackageFull_isPartial0_reasonDropped() {
        enrolled(student(8L, "HS01"));
        attendance(daily(1, 8L, OCT_1, 11).toArray(BillableAttendanceRow[]::new));
        noConflicts();

        GenerateInvoicesResponseDto result = service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"),
                partialSel(8L, "không cần")));

        TuitionFeeEntity fee = savedFees().getFirst();
        assertThat(fee.getTotalSessions()).isEqualTo(10);
        assertThat(fee.getIsPartial()).isZero();
        assertThat(fee.getPartialReason()).isNull();
        assertThat(result.getSlips().getFirst().isPartial()).isFalse();
    }

    @Test
    void generate_allowPartialWithoutReasonEvenWhenFull_isAccepted() {
        enrolled(student(8L, "HS01"));
        attendance(daily(1, 8L, OCT_1, 10).toArray(BillableAttendanceRow[]::new));
        noConflicts();

        assertThat(service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"), partialSel(8L, null)))
                .getCreatedCount()).isEqualTo(1);
    }

    @Test
    void generate_partialMissingOrBlankOrTooLongReason_is400WithDetails_noWrites() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"), student(10L, "HS03"));
        attendance(row(1, 8L, OCT_1), row(2, 9L, OCT_1), row(3, 9L, LocalDate.of(2026, 10, 2)),
                row(4, 10L, OCT_1));
        noConflicts();

        assertThatThrownBy(() -> service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"),
                partialSel(8L, null), partialSel(9L, "   "), partialSel(10L, "x".repeat(201)))))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getCode).containsExactly(
                            TuitionSlipServiceImpl.CODE_PARTIAL_REASON_REQUIRED,
                            TuitionSlipServiceImpl.CODE_PARTIAL_REASON_REQUIRED,
                            TuitionSlipServiceImpl.CODE_PARTIAL_REASON_TOO_LONG);
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getUnbilledCount)
                            .containsExactly(1, 2, 1);
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getField)
                            .containsOnly(TuitionSlipServiceImpl.FIELD_PARTIAL_REASON);
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getStudentId).containsExactly(8L, 9L, 10L);
                    assertThat(details(e)).allSatisfy(d -> assertThat(d.getMessage()).isNotBlank());
                });
        assertNoWrites();
    }

    @Test
    void generate_partialReasonExactly200Chars_isAccepted() {
        enrolled(student(8L, "HS01"));
        attendance(row(1, 8L, OCT_1));
        noConflicts();

        service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"), partialSel(8L, "ạ".repeat(200))));

        assertThat(savedFees().getFirst().getPartialReason()).hasSize(200);
    }

    @Test
    void generate_partialReason201AccentedChars_tooLong_noWrites() {
        enrolled(student(8L, "HS01"));
        attendance(row(1, 8L, OCT_1));
        noConflicts();

        assertThatThrownBy(() -> service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"),
                partialSel(8L, "ạ".repeat(201)))))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getField)
                            .containsExactly(TuitionSlipServiceImpl.FIELD_PARTIAL_REASON);
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getCode)
                            .containsExactly(TuitionSlipServiceImpl.CODE_PARTIAL_REASON_TOO_LONG);
                });
        assertNoWrites();
    }

    @Test
    void generate_partialReason200SurrogatePairs_countedAsChars_accepted() {
        enrolled(student(8L, "HS01"));
        attendance(row(1, 8L, OCT_1));
        noConflicts();
        String reason = "\uD835\uDC00".repeat(200);

        service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"), partialSel(8L, reason)));

        assertThat(savedFees().getFirst().getPartialReason()).isEqualTo(reason);
    }

    @Test
    void generate_teacherComment1000AccentedChars_saved_1001_validation_noWrites() {
        enrolled(student(8L, "HS01"));
        attendance(daily(1, 8L, OCT_1, 10).toArray(BillableAttendanceRow[]::new));
        noConflicts();
        GenerateRangeInvoicesRequestDto ok = withStudents(request(OCT_1, OCT_31, "1000"), sel(8L));
        ok.setTeacherComment("ệ".repeat(1000));
        ok.setFooterWish("ữ".repeat(500));

        service.generateRange(ok);

        assertThat(savedFees().getFirst().getTeacherComment()).hasSize(1000);
        assertThat(savedFees().getFirst().getFooterWish()).hasSize(500);
    }

    @Test
    void generate_teacherCommentOrFooterWishTooLong_validation_noWrites() {
        GenerateRangeInvoicesRequestDto longComment = withStudents(request(OCT_1, OCT_31, "1000"), sel(8L));
        longComment.setTeacherComment("ệ".repeat(1001));
        GenerateRangeInvoicesRequestDto longWish = withStudents(request(OCT_1, OCT_31, "1000"), sel(8L));
        longWish.setFooterWish("ữ".repeat(501));

        assertValidation(() -> service.generateRange(longComment));
        assertValidation(() -> service.generateRange(longWish));
        assertNoWrites();
    }

    // ---- 5. Danh sách học sinh: tất cả hoặc không, details đủ mọi học sinh lỗi (§13.3, §13.6) ------------

    @Test
    void generate_mixedFailures_allListed_validationErrorWins_noWrites() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"), student(10L, "HS03"));
        List<BillableAttendanceRow> rows = new ArrayList<>(daily(1, 8L, OCT_1, 10));
        rows.addAll(daily(100, 9L, OCT_1, 4));
        attendance(rows.toArray(BillableAttendanceRow[]::new));
        noConflicts();

        assertThatThrownBy(() -> service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"),
                sel(8L), sel(9L), sel(99L), sel(10L), sel(8L))))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getStudentId)
                            .containsExactly(9L, 99L, 10L, 8L);
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getCode).containsExactly(
                            TuitionSlipServiceImpl.CODE_STUDENT_NOT_ELIGIBLE,
                            TuitionSlipServiceImpl.CODE_NOT_IN_CLASS,
                            TuitionSlipServiceImpl.CODE_NO_UNBILLED_SESSION,
                            TuitionSlipServiceImpl.CODE_DUPLICATE_STUDENT);
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getUnbilledCount)
                            .containsExactly(4, 0, 0, 10);
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getField)
                            .containsExactly(null, "studentId", null, "studentId");
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getStudentName)
                            .containsExactly("Học sinh HS02", null, "Học sinh HS03", "Học sinh HS01");
                });
        assertNoWrites();
    }

    @Test
    void generate_onlyEligibilityFailures_codeStudentNotEligible_okStudentNotBilled() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        List<BillableAttendanceRow> rows = new ArrayList<>(daily(1, 8L, OCT_1, 10));
        rows.addAll(daily(100, 9L, OCT_1, 9));
        attendance(rows.toArray(BillableAttendanceRow[]::new));
        noConflicts();

        assertThatThrownBy(() -> service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"),
                sel(8L), sel(9L), sel(77L))))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DomainConstants.STUDENT_NOT_ELIGIBLE);
                    assertThat(details(e)).extracting(FeeStudentErrorDto::getStudentId)
                            .containsExactly(9L, 77L);
                    assertThat(details(e).getFirst().getStudentName()).isEqualTo("Học sinh HS02");
                });
        assertNoWrites();
    }

    @Test
    void generate_emptyOrNullStudents_orNullStudentId_isValidationError() {
        for (GenerateRangeInvoicesRequestDto req : List.of(request(OCT_1, OCT_31, "1000"),
                withStudents(request(OCT_1, OCT_31, "1000")))) {
            assertThatThrownBy(() -> service.generateRange(req))
                    .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                        assertThat(details(e)).singleElement().satisfies(d -> {
                            assertThat(d.getStudentId()).isNull();
                            assertThat(d.getStudentName()).isNull();
                            assertThat(d.getField()).isEqualTo("students");
                            assertThat(d.getCode()).isEqualTo(TuitionSlipServiceImpl.CODE_STUDENTS_REQUIRED);
                            assertThat(d.getMessage()).isNotBlank();
                        });
                    });
        }
        enrolled(student(8L, "HS01"));
        attendance();
        noConflicts();
        assertThatThrownBy(() -> service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"), sel(null))))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(details(e).getFirst().getCode())
                            .isEqualTo(TuitionSlipServiceImpl.CODE_STUDENT_ID_REQUIRED);
                    assertThat(details(e).getFirst().getField()).isEqualTo("studentId");
                });
        assertNoWrites();
    }

    @Test
    void generate_multipleStudents_onePerStudent_requestOrder_skippedCountsActiveWithZero() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"), student(10L, "HS03"));
        StudentEntity left = student(7L, "HS00");
        when(studentRepository.findById(7L)).thenReturn(Optional.of(left));
        List<BillableAttendanceRow> rows = new ArrayList<>(daily(1, 8L, OCT_1, 15));
        rows.addAll(daily(100, 7L, OCT_1, 10));
        rows.addAll(daily(200, 9L, OCT_1, 2));
        attendance(rows.toArray(BillableAttendanceRow[]::new));
        noConflicts();

        GenerateInvoicesResponseDto result = service.generateRange(withStudents(request(OCT_1, OCT_31, "1000"),
                sel(8L), sel(7L)));

        assertThat(result.getCreatedCount()).isEqualTo(2);
        assertThat(result.getSkippedNoAttendance()).as("HS03 đang học, 0 buổi").isEqualTo(1);
        assertThat(savedFees()).extracting(TuitionFeeEntity::getStudentId).containsExactly(8L, 7L);
        assertThat(result.getSlips()).extracting(GenerateInvoicesResponseDto.CreatedSlip::getSessionCount)
                .containsExactly(10, 10);
        assertThat(result.getFeeIds()).hasSize(2);
    }

    // ---- QC-1: > 1000 buổi, không IN-list ---------------------------------------------------------------

    @Test
    void generate_moreThan1000AttendanceRows_singleJoinQuery_noIdList() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        List<BillableAttendanceRow> rows = new ArrayList<>();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        for (int i = 0; i < 1200; i++) {
            rows.add(row(10_000 + i, i % 2 == 0 ? 8L : 9L, from.plusDays(i % 365)));
        }
        when(attendanceRepository.findBillableInClassRange(eq(CLASS_ID), eq(from), eq(to), any())).thenReturn(rows);
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), eq(from), eq(to), any()))
                .thenReturn(List.of());

        GenerateInvoicesResponseDto result = service.generateRange(withStudents(request(from, to, "1000"),
                sel(8L), sel(9L)));

        verify(tuitionFeeSessionRepository, times(1))
                .findActiveConflictsInClassRange(CLASS_ID, from, to, DomainConstants.BILLABLE_ATTENDANCE_STATUSES);
        assertThat(result.getCreatedCount()).isEqualTo(2);
        assertThat(savedSessions()).hasSize(20);
    }

    // ---- 6. ORA-00001 (tạo đồng thời) -> 409 (§13.7) -----------------------------------------------------

    @Test
    void generate_uniqueViolationOnSessions_mapsToConflictWithEmptyDetails() {
        twoFullStudents();
        when(tuitionFeeSessionRepository.saveAllAndFlush(anyList()))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(uniqueViolation());

        assertThatThrownBy(() -> service.generateRange(withStudents(request(OCT_1, OCT_31, "80000"),
                sel(8L), sel(9L))))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DomainConstants.FEE_SESSION_CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Có buổi học đã được thu học phí ở phiếu khác.");
                    assertThat(((FeeStudentErrorDto.Payload) e.getData()).details()).isEmpty();
                });
    }

    @Test
    void generate_uniqueViolation_runsInOneTransaction_rolledBackNothingCommitted() {
        twoFullStudents();
        when(tuitionFeeSessionRepository.saveAllAndFlush(anyList()))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(uniqueViolation());
        InMemoryTransactionManager tm = new InMemoryTransactionManager();
        ProxyFactory pf = new ProxyFactory(service);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        TuitionSlipService proxy = (TuitionSlipService) pf.getProxy();

        assertThatThrownBy(() -> proxy.generateRange(withStudents(request(OCT_1, OCT_31, "80000"),
                sel(8L), sel(9L))))
                .isInstanceOf(BusinessDataException.class);

        // Phiếu của học sinh thứ nhất đã saveAndFlush nhưng nằm cùng transaction -> rollback hết.
        assertThat(tm.begun).isEqualTo(1);
        assertThat(tm.commits).isZero();
        assertThat(tm.rollbacks).isEqualTo(1);
        assertThat(tm.depth()).isZero();
    }

    @Test
    void generate_studentValidationFailure_rollsBackInTransaction() {
        enrolled(student(8L, "HS01"));
        attendance(row(1, 8L, OCT_1));
        noConflicts();
        InMemoryTransactionManager tm = new InMemoryTransactionManager();
        ProxyFactory pf = new ProxyFactory(service);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));

        assertThatThrownBy(() -> ((TuitionSlipService) pf.getProxy())
                .generateRange(withStudents(request(OCT_1, OCT_31, "80000"), sel(8L))))
                .isInstanceOf(BusinessDataException.class);

        assertThat(tm.commits).isZero();
        assertThat(tm.rollbacks).isEqualTo(1);
        assertNoWrites();
    }

    @Test
    void generate_success_singleTransactionCommitted() {
        twoFullStudents();
        InMemoryTransactionManager tm = new InMemoryTransactionManager();
        ProxyFactory pf = new ProxyFactory(service);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));

        ((TuitionSlipService) pf.getProxy()).generateRange(withStudents(request(OCT_1, OCT_31, "80000"),
                sel(8L), sel(9L)));

        assertThat(tm.begun).isEqualTo(1);
        assertThat(tm.commits).isEqualTo(1);
        assertThat(tm.rollbacks).isZero();
    }

    @Test
    void generate_otherIntegrityViolation_isNotMappedToConflict() {
        enrolled(student(8L, "HS01"));
        attendance(row(1, 8L, OCT_1));
        noConflicts();
        when(tuitionFeeSessionRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("ORA-02291: integrity constraint (EDU.FK_X) violated"));

        assertThatThrownBy(() -> service.generateRange(withStudents(request(OCT_1, OCT_31, "80000"),
                partialSel(8L, "lý do"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void isFeeSessionUniqueViolation_searchesCauseChain() {
        assertThat(TuitionSlipServiceImpl.isFeeSessionUniqueViolation(uniqueViolation())).isTrue();
        assertThat(TuitionSlipServiceImpl.isFeeSessionUniqueViolation(
                new RuntimeException("wrap", new RuntimeException("ORA-00001: unique constraint (EDU.UQ_FEES_MONTHLY)"))))
                .isFalse();
    }

    // ---- 7. Hai khoảng rời nhau trong cùng tháng ---------------------------------------------------------

    @Test
    void generate_twoDisjointRangesSameMonth_bothSucceed_withNullMonth() {
        enrolled(student(8L, "HS01"));
        LocalDate oct15 = LocalDate.of(2026, 10, 15);
        LocalDate oct16 = LocalDate.of(2026, 10, 16);
        when(attendanceRepository.findBillableInClassRange(eq(CLASS_ID), eq(OCT_1), eq(oct15), any()))
                .thenReturn(List.of(row(1, 8L, OCT_1)));
        when(attendanceRepository.findBillableInClassRange(eq(CLASS_ID), eq(oct16), eq(OCT_31), any()))
                .thenReturn(List.of(row(2, 8L, LocalDate.of(2026, 10, 20))));
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.generateRange(withStudents(request(OCT_1, oct15, "80000"), partialSel(8L, "a")))
                .getCreatedCount()).isEqualTo(1);
        assertThat(service.generateRange(withStudents(request(oct16, OCT_31, "80000"), partialSel(8L, "b")))
                .getCreatedCount()).isEqualTo(1);

        List<TuitionFeeEntity> fees = savedFees();
        assertThat(fees).hasSize(2);
        assertThat(fees).allSatisfy(f -> {
            assertThat(f.getFeeMonth()).as("UQ_FEES_MONTHLY không áp dụng").isNull();
            assertThat(f.getFeeYear()).isNull();
        });
    }

    // ---- 8. Validation -> 400 VALIDATION_ERROR --------------------------------------------------------

    @Test
    void validation_fromAfterTo_rangeOver366_missingOrBadPrice() {
        assertValidation(() -> service.generateRange(request(OCT_31, OCT_1, "80000")));
        assertValidation(() -> service.generateRange(request(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2), "80000")));
        assertValidation(() -> service.generateRange(request(OCT_1, OCT_31, null)));
        assertValidation(() -> service.generateRange(request(OCT_1, OCT_31, "0")));
        assertValidation(() -> service.generateRange(request(OCT_1, OCT_31, "1000.555")));
        assertValidation(() -> service.generateRange(request(null, OCT_31, "1000")));
        assertValidation(() -> service.previewBillable(CLASS_ID, OCT_31, OCT_1, null));
        assertValidation(() -> service.previewBillable(CLASS_ID, OCT_1, OCT_31, new BigDecimal("-1")));
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
    }

    @Test
    void validation_exactly366DaysInclusive_isAccepted() {
        // 2028 nhuận: 01/01/2028..31/12/2028 = 366 ngày tính cả hai đầu.
        LocalDate from = LocalDate.of(2028, 1, 1);
        LocalDate to = LocalDate.of(2028, 12, 31);
        enrolled();
        when(attendanceRepository.findBillableInClassRange(eq(CLASS_ID), eq(from), eq(to), any())).thenReturn(List.of());
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), eq(from), eq(to), any()))
                .thenReturn(List.of());

        assertThat(service.previewBillable(CLASS_ID, from, to, null).getStudents()).isEmpty();
        assertValidation(() -> service.previewBillable(CLASS_ID, from, to.plusDays(1), null));
    }

    @Test
    void classNotFound_is404Code() {
        when(classRepository.findByIdAndIsDeleted(999L, 0)).thenReturn(Optional.empty());
        GenerateRangeInvoicesRequestDto req = withStudents(request(OCT_1, OCT_31, "1000"), sel(8L));
        req.setClassId(999L);
        assertThatThrownBy(() -> service.generateRange(req))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("CLASS_NOT_FOUND");
    }

    // ---- 11. Xem trước chỉ đọc: unbilledCount / eligible / packageDates (v1.4 §13, §13.4) ------------------

    @Test
    void preview_unbilledOnly_eligible_packageDates_inactive_zeroSessionActive_readOnly() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student(7L, "HS00")));
        List<BillableAttendanceRow> rows = new ArrayList<>();
        rows.add(row(1, 7L, LocalDate.of(2026, 10, 2)));
        rows.addAll(daily(100, 8L, OCT_1, 13));
        attendance(rows.toArray(BillableAttendanceRow[]::new));
        // Buổi 01/10 của HS01 đã nằm trên phiếu hiệu lực -> ẩn, không đếm lại.
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), eq(OCT_1), eq(OCT_31), any()))
                .thenReturn(List.of(new FeeSessionConflictRow(100L, 8L, OCT_1, 50L, "HP-1")));

        BillableSessionsPreviewDto p = service.previewBillable(CLASS_ID, OCT_1, OCT_31, new BigDecimal("150000"));

        assertThat(p.getStudents()).extracting(BillableSessionsPreviewDto.StudentRow::getStudentCode)
                .containsExactly("HS00", "HS01", "HS02");
        BillableSessionsPreviewDto.StudentRow left = p.getStudents().get(0);
        assertThat(left.isInactive()).isTrue();
        assertThat(left.getUnbilledCount()).isEqualTo(1);
        assertThat(left.isEligible()).isFalse();
        assertThat(left.getPackageDates()).containsExactly("02/10");
        assertThat(left.getTotalAmount()).isEqualByComparingTo("150000");

        BillableSessionsPreviewDto.StudentRow a = p.getStudents().get(1);
        assertThat(a.isInactive()).isFalse();
        assertThat(a.getUnbilledCount()).isEqualTo(12);
        assertThat(a.getSessionCount()).isEqualTo(12);
        assertThat(a.getSessionDates()).hasSize(12).first().isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(a.isEligible()).isTrue();
        assertThat(a.getPackageDates()).containsExactly("02/10", "03/10", "04/10", "05/10", "06/10",
                "07/10", "08/10", "09/10", "10/10", "11/10");
        assertThat(a.getTotalAmount()).as("10 buổi x giá").isEqualByComparingTo("1500000");
        assertThat(a.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(a.getNetAmount()).isEqualByComparingTo("1500000");
        assertThat(a.getConflictDates()).as("buổi đã thu không hiển thị (§13.4)").isEmpty();
        assertThat(a.getConflictFeeCodes()).isEmpty();

        BillableSessionsPreviewDto.StudentRow zero = p.getStudents().get(2);
        assertThat(zero.getUnbilledCount()).isZero();
        assertThat(zero.isEligible()).isFalse();
        assertThat(zero.getPackageDates()).isEmpty();

        assertThat(p.getTotals().getStudentCount()).isEqualTo(3);
        assertThat(p.getTotals().getBillableStudentCount()).isEqualTo(2);
        assertThat(p.getTotals().getSkippedNoAttendance()).isEqualTo(1);
        assertThat(p.getTotals().getSessionCount()).isEqualTo(13);
        assertThat(p.getTotals().getEligibleStudentCount()).isEqualTo(1);
        assertThat(p.getTotals().getTotalAmount()).isEqualByComparingTo("1650000");
        assertThat(p.getTotals().getConflictStudentCount()).isZero();
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
        verify(tuitionFeeRepository, never()).nextTuitionFeeCode();
        verify(tuitionFeeSessionRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void preview_listsAllActiveStudents_includingZeroAndFullyBilled_withEmptyPackage() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"), student(10L, "HS03"));
        List<BillableAttendanceRow> rows = new ArrayList<>(daily(1, 8L, OCT_1, 3));
        rows.addAll(daily(100, 9L, OCT_1, 10));
        attendance(rows.toArray(BillableAttendanceRow[]::new));
        // HS01: cả 3 buổi đã nằm trên phiếu hiệu lực -> 0 chưa thu nhưng vẫn liệt kê (đang học).
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), any(), any(), any()))
                .thenReturn(List.of(new FeeSessionConflictRow(1L, 8L, OCT_1, 50L, "HP-1"),
                        new FeeSessionConflictRow(2L, 8L, OCT_1.plusDays(1), 50L, "HP-1"),
                        new FeeSessionConflictRow(3L, 8L, OCT_1.plusDays(2), 50L, "HP-1")));

        BillableSessionsPreviewDto p = service.previewBillable(CLASS_ID, OCT_1, OCT_31, new BigDecimal("1000"));

        assertThat(p.getStudents()).extracting(BillableSessionsPreviewDto.StudentRow::getStudentId)
                .containsExactly(8L, 9L, 10L);
        for (BillableSessionsPreviewDto.StudentRow zero : List.of(p.getStudents().get(0), p.getStudents().get(2))) {
            assertThat(zero.getUnbilledCount()).isZero();
            assertThat(zero.isEligible()).isFalse();
            assertThat(zero.getPackageDates()).isNotNull().isEmpty();
            assertThat(zero.getSessionDates()).isEmpty();
            assertThat(zero.getTotalAmount()).isEqualByComparingTo("0");
            assertThat(zero.isInactive()).isFalse();
        }
        assertThat(p.getStudents().get(1).isEligible()).isTrue();
        assertThat(p.getStudents().get(1).getPackageDates()).hasSize(10);
        assertThat(p.getTotals().getStudentCount()).isEqualTo(3);
        assertThat(p.getTotals().getSkippedNoAttendance()).isEqualTo(2);
        assertThat(p.getTotals().getEligibleStudentCount()).isEqualTo(1);
    }

    @Test
    void preview_inactiveWithZeroOrOnlyBilledSessions_notShown_andNoPriceMeansNullMoney() {
        ClassStudentEntity dropped = ClassStudentEntity.builder().classId(CLASS_ID).studentId(7L)
                .status("DROPPED").isDeleted(0).build();
        StudentEntity a = student(8L, "HS01");
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(a));
        lenient().when(studentRepository.findById(6L)).thenReturn(Optional.of(student(6L, "HS06")));
        when(classStudentRepository.findByClassIdAndIsDeleted(CLASS_ID, 0)).thenReturn(List.of(dropped,
                ClassStudentEntity.builder().classId(CLASS_ID).studentId(8L).status("ENROLLED").isDeleted(0).build()));
        attendance(row(1, 8L, OCT_1), row(2, 6L, OCT_1));
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), any(), any(), any()))
                .thenReturn(List.of(new FeeSessionConflictRow(2L, 6L, OCT_1, 50L, "HP-1")));

        BillableSessionsPreviewDto p = service.previewBillable(CLASS_ID, OCT_1, OCT_31, null);

        assertThat(p.getStudents()).extracting(BillableSessionsPreviewDto.StudentRow::getStudentId).containsExactly(8L);
        assertThat(p.getPricePerSession()).isNull();
        assertThat(p.getStudents().getFirst().getTotalAmount()).isNull();
        assertThat(p.getStudents().getFirst().getDiscountAmount()).isNull();
        assertThat(p.getStudents().getFirst().getNetAmount()).isNull();
        assertThat(p.getTotals().getTotalAmount()).isNull();
        assertThat(p.getTotals().getNetAmount()).isNull();
    }

    // ---- 9. Phiếu: kỳ thu, ngày từ bảng session, không "(muộn)" -----------------------------------------

    @Test
    void getSlip_rangeFee_periodTextAndSortedBadgesFromSnapshot() {
        TuitionFeeEntity fee = rangeFee(9L, "UNPAID");
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee));
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(procSlip(9L, List.of("05/10", "30/10", "31/10")));
        when(tuitionFeeSessionRepository.findByFeeIdOrderBySessionDateAscIdAsc(9L)).thenReturn(List.of(
                session(9L, LocalDate.of(2026, 10, 9)), session(9L, LocalDate.of(2026, 10, 2))));

        TuitionSlipResponseDto slip = service.getSlip(9L);

        assertThat(slip.getPeriodText()).isEqualTo("Từ 01/10/2026 đến 31/10/2026");
        assertThat(slip.getMonthYearText()).isEqualTo("Từ 01/10/2026 đến 31/10/2026");
        assertThat(slip.getPeriodFrom()).isEqualTo(OCT_1);
        assertThat(slip.getPeriodTo()).isEqualTo(OCT_31);
        assertThat(slip.isRangeFee()).isTrue();
        assertThat(slip.getAttendedDates()).containsExactly("02/10", "09/10");
        assertThat(slip.getQrPayload()).contains("HS000123 HP 0110-3110");
        // "Nguyễn Văn A": chỉ còn 3 chỗ, "NGUYEN" không vừa -> bỏ tên; transferContent == addInfo trong QR / quick link.
        assertThat(slip.getTransferContent()).isEqualTo("HS000123 HP 0110-3110");
        assertQrCarries(slip.getQrPayload(), slip.getQuickPayUrl(), slip.getTransferContent());
        String html = service.generateSlipHtml(slip);
        assertThat(html).contains("Từ 01/10/2026 đến 31/10/2026").contains("02/10").doesNotContain("muộn");
    }

    @Test
    void getSlip_partialFee_exposesPartialAndReason_fullFeeIsNotPartial() {
        TuitionFeeEntity fee = rangeFee(9L, "UNPAID");
        fee.setIsPartial(1);
        fee.setPartialReason("Nghỉ giữa khóa");
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee));
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(procSlip(9L, List.of()));
        when(tuitionFeeSessionRepository.findByFeeIdOrderBySessionDateAscIdAsc(9L)).thenReturn(List.of());

        TuitionSlipResponseDto slip = service.getSlip(9L);
        assertThat(slip.isPartial()).isTrue();
        assertThat(slip.getPartialReason()).isEqualTo("Nghỉ giữa khóa");

        TuitionFeeEntity full = rangeFee(10L, "UNPAID");
        when(tuitionFeeRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(full));
        when(tuitionFeeRepository.getTuitionSlipData(10L)).thenReturn(procSlip(10L, List.of()));
        when(tuitionFeeSessionRepository.findByFeeIdOrderBySessionDateAscIdAsc(10L)).thenReturn(List.of());
        assertThat(service.getSlip(10L).isPartial()).isFalse();
        assertThat(full.getIsPartial()).as("@Builder.Default 0").isZero();
    }

    @Test
    void getSlip_cancelledRangeFee_noBadges_neverReadsAttendance() {
        TuitionFeeEntity fee = rangeFee(9L, "CANCELLED");
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee));
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(procSlip(9L, List.of("05/10")));

        TuitionSlipResponseDto slip = service.getSlip(9L);

        assertThat(slip.getAttendedDates()).isEmpty();
        assertThat(slip.getStatus()).isEqualTo("CANCELLED");
        assertThat(slip.getQrPayload()).isNull();
        verify(tuitionFeeSessionRepository, never()).findByFeeIdOrderBySessionDateAscIdAsc(anyLong());
        verify(attendanceRepository, never()).findBillableInClassRange(anyLong(), any(), any(), any());
        assertThat(service.generateSlipHtml(slip)).contains("Phiếu đã hủy");
    }

    @Test
    void getSlip_legacyMonthlyFee_keepsMonthText_andFallsBackToProcedureDates() {
        TuitionFeeEntity fee = TuitionFeeEntity.builder().id(9L).feeCode("HP9").studentId(8L).classId(CLASS_ID)
                .feeMonth(4).feeYear(2026).periodFrom(LocalDate.of(2026, 4, 1)).periodTo(LocalDate.of(2026, 4, 30))
                .totalAmount(new BigDecimal("160000")).discountAmount(BigDecimal.ZERO).paidAmount(BigDecimal.ZERO)
                .status("UNPAID").isDeleted(0).build();
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee));
        TuitionSlipResponseDto data = procSlip(9L, List.of("02/04", "06/04"));
        data.setMonth(4);
        data.setYear(2026);
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(data);
        when(tuitionFeeSessionRepository.findByFeeIdOrderBySessionDateAscIdAsc(9L)).thenReturn(List.of());

        TuitionSlipResponseDto slip = service.getSlip(9L);

        assertThat(slip.getPeriodText()).isEqualTo("Tháng 04/2026");
        assertThat(slip.getMonthYearText()).isEqualTo("Tháng 04/2026");
        assertThat(slip.isRangeFee()).isFalse();
        assertThat(slip.getAttendedDates()).containsExactly("02/04", "06/04");
        assertThat(slip.getQrPayload()).contains("HS000123 HP T4");
        assertThat(slip.getTransferContent()).isEqualTo("NGUYEN VAN HS000123 HP T4");
        assertQrCarries(slip.getQrPayload(), slip.getQuickPayUrl(), slip.getTransferContent());
    }

    @Test
    void getSlip_blankStudentName_keepsOldContent_andNoQrMeansNullTransferContent() {
        TuitionFeeEntity fee = rangeFee(9L, "UNPAID");
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee));
        TuitionSlipResponseDto data = procSlip(9L, List.of());
        data.setStudentName(" ");
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(data);
        when(tuitionFeeSessionRepository.findByFeeIdOrderBySessionDateAscIdAsc(9L)).thenReturn(List.of());

        TuitionSlipResponseDto slip = service.getSlip(9L);
        assertThat(slip.getTransferContent()).isEqualTo("HS000123 HP 0110-3110");
        assertQrCarries(slip.getQrPayload(), slip.getQuickPayUrl(), slip.getTransferContent());

        TuitionFeeEntity cancelled = rangeFee(10L, "CANCELLED");
        when(tuitionFeeRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(cancelled));
        when(tuitionFeeRepository.getTuitionSlipData(10L)).thenReturn(procSlip(10L, List.of()));
        TuitionSlipResponseDto none = service.getSlip(10L);
        assertThat(none.getQrPayload()).isNull();
        assertThat(none.getTransferContent()).isNull();
    }

    /** addInfo trong QR (tag 62 / sub-tag 08) và quick link đúng bằng {@code transferContent}. */
    static void assertQrCarries(String payload, String quickUrl, String transferContent) {
        String sub = "08" + String.format("%02d", transferContent.length()) + transferContent;
        String tag62 = "62" + String.format("%02d", sub.length()) + sub;
        assertThat(payload).contains(tag62);
        assertThat(java.net.URLDecoder.decode(quickUrl, java.nio.charset.StandardCharsets.UTF_8))
                .contains("addInfo=" + transferContent);
    }

    @Test
    void getSlip_manualFeeWithoutMonth_isNotRange_usesProcedureData() {
        TuitionFeeEntity fee = TuitionFeeEntity.builder().id(9L).feeCode("HP9").studentId(8L).classId(CLASS_ID)
                .totalAmount(new BigDecimal("160000")).discountAmount(new BigDecimal("10000"))
                .paidAmount(BigDecimal.ZERO).status("UNPAID").isDeleted(0).build();
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee));
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(procSlip(9L, List.of("03/11")));

        TuitionSlipResponseDto slip = service.getSlip(9L);

        assertThat(fee.getIsRange()).isZero();
        assertThat(slip.isRangeFee()).isFalse();
        assertThat(slip.getAttendedDates()).containsExactly("03/11");
        assertThat(slip.getDiscountAmount()).isEqualByComparingTo("10000");
        verify(tuitionFeeSessionRepository, never()).findByFeeIdOrderBySessionDateAscIdAsc(anyLong());
    }

    // ---- BUG-FEE-RANGE-03: khoản tạo tay không có tên trên mọi đường QR (BA §15.3) ---------------------------

    @Test
    void getSlip_manualFee_qrHasNoName_exactPreviousContent_monthlyAndRangeHaveName() {
        TuitionFeeEntity manual = TuitionFeeEntity.builder().id(9L).feeCode("HP9").studentId(8L).classId(CLASS_ID)
                .totalAmount(new BigDecimal("160000")).discountAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO).status("UNPAID").isDeleted(0).build();
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(manual));
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(procSlip(9L, List.of("03/11")));

        TuitionSlipResponseDto slip = service.getSlip(9L);

        // procSlip: "Nguyễn Văn A", HS000123, tháng 11 -> trước đây "HS000123 HP T11".
        assertThat(slip.getTransferContent()).isEqualTo("HS000123 HP T11")
                .isEqualTo(TuitionSlipServiceImpl.transferContent("HS000123", 11));
        assertQrCarries(slip.getQrPayload(), slip.getQuickPayUrl(), slip.getTransferContent());
        assertThat(slip.getQrPayload()).doesNotContain("NGUYEN");
        // PNG / bản in dùng chính dữ liệu phiếu này.
        assertThat(service.generateSlipHtml(slip)).doesNotContain("NGUYEN VAN");

        TuitionFeeEntity monthly = TuitionFeeEntity.builder().id(10L).feeCode("HP10").studentId(8L).classId(CLASS_ID)
                .feeMonth(11).feeYear(2026).totalAmount(new BigDecimal("160000")).discountAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO).status("UNPAID").isDeleted(0).build();
        when(tuitionFeeRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(monthly));
        when(tuitionFeeRepository.getTuitionSlipData(10L)).thenReturn(procSlip(10L, List.of("03/11")));
        lenient().when(tuitionFeeSessionRepository.findByFeeIdOrderBySessionDateAscIdAsc(10L)).thenReturn(List.of());
        TuitionSlipResponseDto m = service.getSlip(10L);
        // "HS000123 HP T11" = 15 -> 9 chỗ: "NGUYEN VAN" (10) không vừa -> chỉ "NGUYEN".
        assertThat(m.getTransferContent()).isEqualTo("NGUYEN HS000123 HP T11");
        assertQrCarries(m.getQrPayload(), m.getQuickPayUrl(), m.getTransferContent());

        TuitionFeeEntity range = rangeFee(11L, "UNPAID");
        range.setPeriodTo(LocalDate.of(2026, 10, 10));
        StudentEntity shortName = student(8L, "SV01");
        assertThat(TuitionSlipServiceImpl.transferContentOf(range, "Lê Văn An", shortName.getStudentCode(), null))
                .isEqualTo("LE VAN SV01 HP 0110-1010");
        assertThat(TuitionSlipServiceImpl.transferContentOf(manual, "Lê Văn An", "SV01", 4)).isEqualTo("SV01 HP T4");
        assertThat(TuitionSlipServiceImpl.transferContentOf(monthly, "Lê Văn An", "SV01", 4))
                .isEqualTo("LE VAN AN SV01 HP T4");
    }

    // ---- 10. VietQR ------------------------------------------------------------------------------------

    @Test
    void rangeTransferContent_formatAndLimits() {
        assertThat(FeePeriods.rangeTransferContent("HS000123", OCT_1, OCT_31)).isEqualTo("HS000123 HP 0110-3110");
        assertThat(FeePeriods.rangeTransferContent("HS-0001/2", OCT_1, OCT_31)).isEqualTo("HS00012 HP 0110-3110");
        assertThat(FeePeriods.rangeTransferContent("ABCDEFGHIJKL", OCT_1, OCT_31)).isEqualTo("ABCDEFGHIJKL HP 0110-3110");
        assertThat(FeePeriods.isRangeTransferCodeTruncated("ABCDEFGHIJKL")).isFalse();
        assertThat(FeePeriods.isRangeTransferCodeTruncated("ABCDEFGHIJKLM")).isTrue();
        StringBuilder code = new StringBuilder();
        for (int len = 0; len < 60; len++) {
            String text = FeePeriods.rangeTransferContent("Học sinh " + code, LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 12, 25));
            assertThat(text.length()).isLessThanOrEqualTo(25);
            assertThat(text).endsWith(" HP 0501-2512").doesNotContain("/").matches("\\p{ASCII}+");
            code.append((char) ('A' + len % 26));
        }
    }

    @Test
    void legacyTransferContent_unchanged() {
        assertThat(TuitionSlipServiceImpl.transferContent("SV20260001", 4)).isEqualTo("SV20260001 HP T4");
        assertThat(TuitionSlipServiceImpl.transferContent("Sinh Viên", 12)).isEqualTo("SinhVien HP T12");
        assertThat(TuitionSlipServiceImpl.transferContent("THISCODEISTOOLONGFORNAPASXX", 4).length())
                .isLessThanOrEqualTo(25);
    }

    // ---- Không đổi -------------------------------------------------------------------------------------

    @Test
    void resolveDueDate_pastPeriodGetsGracePeriod() {
        LocalDate today = LocalDate.of(2026, 10, 2);
        assertThat(TuitionSlipServiceImpl.resolveDueDate(LocalDate.of(2026, 4, 30), today))
                .isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(TuitionSlipServiceImpl.resolveDueDate(OCT_31, today)).isEqualTo(OCT_31);
    }

    @Test
    void isQrPayable_requiresPositiveRemainingAndOpenStatus() {
        assertThat(TuitionSlipServiceImpl.isQrPayable("UNPAID", 1)).isTrue();
        assertThat(TuitionSlipServiceImpl.isQrPayable("PARTIAL", 0)).isFalse();
        assertThat(TuitionSlipServiceImpl.isQrPayable("PAID", 100)).isFalse();
        assertThat(TuitionSlipServiceImpl.isQrPayable("CANCELLED", 100)).isFalse();
    }

    @Test
    void getSlip_partiallyPaid_qrUsesRemaining() {
        TuitionFeeEntity fee = rangeFee(9L, "PARTIAL");
        fee.setTotalAmount(new BigDecimal("960000"));
        fee.setPaidAmount(new BigDecimal("300000"));
        when(tuitionFeeRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(fee));
        when(tuitionFeeRepository.getTuitionSlipData(9L)).thenReturn(procSlip(9L, List.of()));
        when(tuitionFeeSessionRepository.findByFeeIdOrderBySessionDateAscIdAsc(9L)).thenReturn(List.of());

        TuitionSlipResponseDto slip = service.getSlip(9L);

        assertThat(slip.getRemainingAmount()).isEqualByComparingTo("660000");
        assertThat(slip.getQrPayload()).contains("5406660000");
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
        assertThat(html).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;").doesNotContain("<b>");
    }

    @Test
    void formatMonthYear_padsMonth_andQrPayloadContainsAmount() {
        assertThat(TuitionSlipServiceImpl.formatMonthYear(4, 2026)).isEqualTo("Tháng 04/2026");
        String payload = VietQrHelper.buildVietQrPayload("970436", "1234567890", 960000L, "SV01 HP T4");
        assertThat(payload).contains("5406960000");
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    static void assertValidation(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    static DataIntegrityViolationException uniqueViolation() {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ORA-00001: unique constraint (EDUCATION.UQ_FEE_SESSIONS_ATTENDANCE) violated"));
    }

    void enrolled(StudentEntity... students) {
        List<ClassStudentEntity> enrollments = new ArrayList<>();
        for (StudentEntity s : students) {
            enrollments.add(ClassStudentEntity.builder().classId(CLASS_ID).studentId(s.getId())
                    .status("ENROLLED").isDeleted(0).build());
            lenient().when(studentRepository.findByIdAndIsDeleted(s.getId(), 0)).thenReturn(Optional.of(s));
        }
        when(classStudentRepository.findByClassIdAndIsDeleted(CLASS_ID, 0)).thenReturn(enrollments);
    }

    void attendance(BillableAttendanceRow... rows) {
        when(attendanceRepository.findBillableInClassRange(eq(CLASS_ID), any(), any(), any()))
                .thenReturn(List.of(rows));
    }

    void noConflicts() {
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), any(), any(), any()))
                .thenReturn(List.of());
    }

    List<TuitionFeeEntity> savedFees() {
        ArgumentCaptor<TuitionFeeEntity> captor = ArgumentCaptor.forClass(TuitionFeeEntity.class);
        verify(tuitionFeeRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getAllValues();
    }

    @SuppressWarnings("unchecked")
    List<List<TuitionFeeSessionEntity>> sessionBatches() {
        ArgumentCaptor<List<TuitionFeeSessionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(tuitionFeeSessionRepository, org.mockito.Mockito.atLeastOnce()).saveAllAndFlush(captor.capture());
        return captor.getAllValues();
    }

    List<TuitionFeeSessionEntity> savedSessions() {
        List<TuitionFeeSessionEntity> all = new ArrayList<>();
        sessionBatches().forEach(all::addAll);
        return all;
    }

    static BillableAttendanceRow row(long attendanceId, long studentId, LocalDate date) {
        return new BillableAttendanceRow(attendanceId, studentId, date);
    }

    static StudentEntity student(long id, String code) {
        return StudentEntity.builder().id(id).studentCode(code).fullName("Học sinh " + code)
                .status("ACTIVE").isDeleted(0).build();
    }

    static GenerateRangeInvoicesRequestDto request(LocalDate from, LocalDate to, String price) {
        return GenerateRangeInvoicesRequestDto.builder().classId(CLASS_ID).fromDate(from).toDate(to)
                .pricePerSession(price == null ? null : new BigDecimal(price)).build();
    }

    static GenerateRangeInvoicesRequestDto withStudents(GenerateRangeInvoicesRequestDto req,
                                                        GenerateRangeInvoicesRequestDto.StudentSelection... sels) {
        req.setStudents(new ArrayList<>(java.util.Arrays.asList(sels)));
        return req;
    }

    static GenerateRangeInvoicesRequestDto.StudentSelection sel(Long studentId) {
        return GenerateRangeInvoicesRequestDto.StudentSelection.builder().studentId(studentId).build();
    }

    static GenerateRangeInvoicesRequestDto.StudentSelection partialSel(Long studentId, String reason) {
        return GenerateRangeInvoicesRequestDto.StudentSelection.builder().studentId(studentId).allowPartial(true)
                .partialReason(reason).build();
    }

    /** {@code count} buổi liên tiếp mỗi ngày từ {@code start}, attendanceId tăng dần từ {@code firstId}. */
    static List<BillableAttendanceRow> daily(long firstId, long studentId, LocalDate start, int count) {
        List<BillableAttendanceRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(row(firstId + i, studentId, start.plusDays(i)));
        }
        return rows;
    }

    void twoFullStudents() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        List<BillableAttendanceRow> rows = new ArrayList<>(daily(1, 8L, OCT_1, 10));
        rows.addAll(daily(100, 9L, OCT_1, 10));
        attendance(rows.toArray(BillableAttendanceRow[]::new));
        noConflicts();
    }

    static List<FeeStudentErrorDto> details(BusinessDataException e) {
        return ((FeeStudentErrorDto.Payload) e.getData()).details();
    }

    void assertNoWrites() {
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
        verify(tuitionFeeRepository, never()).nextTuitionFeeCode();
        verify(tuitionFeeSessionRepository, never()).saveAllAndFlush(anyList());
    }

    static TuitionFeeEntity rangeFee(long id, String status) {
        return TuitionFeeEntity.builder().id(id).feeCode("HP" + id).studentId(8L).classId(CLASS_ID)
                .periodFrom(OCT_1).periodTo(OCT_31).isRange(1).totalAmount(new BigDecimal("160000"))
                .discountAmount(BigDecimal.ZERO).paidAmount(BigDecimal.ZERO).status(status).isDeleted(0).build();
    }

    static TuitionSlipResponseDto procSlip(long id, List<String> procDates) {
        return TuitionSlipResponseDto.builder().invoiceId(id).invoiceCode("HP" + id).month(11).year(2026)
                .studentCode("HS000123").studentName("Nguyễn Văn A").totalSessions(2)
                .totalAmount(new BigDecimal("160000")).attendedDates(new ArrayList<>(procDates)).build();
    }

    static TuitionFeeSessionEntity session(long feeId, LocalDate date) {
        return TuitionFeeSessionEntity.builder().feeId(feeId).sessionDate(date).build();
    }
}
