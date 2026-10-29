package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FeePeriods;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.request.GenerateRangeInvoicesRequestDto;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.BillableSessionsPreviewDto;
import com.education.base.dto.response.FeeSessionConflictDto;
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
 * Thu học phí theo khoảng ngày (SPEC-fee-date-range v1.1 mục 8, contract fee-range-api v1.3).
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

    // ---- 1. Buổi tính phí: PRESENT + LATE, chưa xóa, cả hai đầu ----------------------------------------

    @Test
    void generate_queriesBillableStatusesWithInclusiveRange_andBillsEachRow() {
        enrolled(student(8L, "HS01"));
        attendance(row(101, 8L, OCT_1), row(102, 8L, LocalDate.of(2026, 10, 15)), row(103, 8L, OCT_31));
        noConflicts();

        GenerateInvoicesResponseDto result = service.generateRange(request(OCT_1, OCT_31, "80000"));

        verify(attendanceRepository).findBillableInClassRange(CLASS_ID, OCT_1, OCT_31,
                DomainConstants.BILLABLE_ATTENDANCE_STATUSES);
        assertThat(DomainConstants.BILLABLE_ATTENDANCE_STATUSES).containsExactly("PRESENT", "LATE");
        TuitionFeeEntity fee = savedFees().getFirst();
        assertThat(fee.getTotalSessions()).isEqualTo(3);
        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getFeeIds()).containsExactly(fee.getId());
        assertThat(savedSessions()).extracting(TuitionFeeSessionEntity::getAttendanceId).containsExactly(101L, 102L, 103L);
        assertThat(savedSessions()).extracting(TuitionFeeSessionEntity::getSessionDate)
                .containsExactly(OCT_1, LocalDate.of(2026, 10, 15), OCT_31);
    }

    // ---- 2. Tiền = số buổi x đơn giá, scale 2, không giảm giá ------------------------------------------

    @Test
    void generate_amountIsSessionsTimesPrice_discountZero_periodSet_monthNull() {
        enrolled(student(8L, "HS01"));
        attendance(row(1, 8L, OCT_1), row(2, 8L, LocalDate.of(2026, 10, 8)));
        noConflicts();

        service.generateRange(request(OCT_1, OCT_31, "80000.5"));

        TuitionFeeEntity fee = savedFees().getFirst();
        assertThat(fee.getTotalAmount()).isEqualByComparingTo("160001.00");
        assertThat(fee.getTotalAmount().scale()).isEqualTo(2);
        assertThat(fee.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(fee.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(fee.getPricePerSession()).isEqualByComparingTo("80000.50");
        assertThat(fee.getFeeMonth()).isNull();
        assertThat(fee.getFeeYear()).isNull();
        assertThat(fee.getPeriodFrom()).isEqualTo(OCT_1);
        assertThat(fee.getPeriodTo()).isEqualTo(OCT_31);
        assertThat(fee.getIsRange()).as("chỉ generate-range ghi IS_RANGE = 1").isEqualTo(1);
        assertThat(fee.getNote()).isEqualTo("HP 01/10/2026-31/10/2026");
        assertThat(fee.getSlipLabel()).isEqualTo(DomainConstants.TUITION_SLIP_LABEL_DEFAULT);
        assertThat(fee.getStatus()).isIn("UNPAID", "OVERDUE");
        assertThat(fee.getDueDate()).isEqualTo(TuitionSlipServiceImpl.resolveDueDate(OCT_31, LocalDate.now()));
    }

    // ---- 3. Xung đột -> 409, tất cả hoặc không --------------------------------------------------------

    @Test
    void generate_conflictWithActiveFee_rejectsWholeRequest_listsExactDatesAndFeeCodes() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        attendance(row(1, 8L, OCT_1), row(2, 8L, LocalDate.of(2026, 10, 9)), row(3, 9L, OCT_1));
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), eq(OCT_1), eq(OCT_31), any()))
                .thenReturn(List.of(
                        new FeeSessionConflictRow(2L, 8L, LocalDate.of(2026, 10, 9), 50L, "HP-000812"),
                        new FeeSessionConflictRow(1L, 8L, OCT_1, 51L, "HP-000813")));

        assertThatThrownBy(() -> service.generateRange(request(OCT_1, OCT_31, "80000")))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DomainConstants.FEE_SESSION_CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Có buổi học đã được thu học phí ở phiếu khác.");
                    FeeSessionConflictDto.Payload payload = (FeeSessionConflictDto.Payload) e.getData();
                    assertThat(payload.details()).hasSize(1);
                    FeeSessionConflictDto d = payload.details().getFirst();
                    assertThat(d.getStudentId()).isEqualTo(8L);
                    assertThat(d.getStudentCode()).isEqualTo("HS01");
                    assertThat(d.getDates()).containsExactly(OCT_1, LocalDate.of(2026, 10, 9));
                    assertThat(d.getFeeCodes()).containsExactly("HP-000812", "HP-000813");
                });
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
        verify(tuitionFeeRepository, never()).nextTuitionFeeCode();
        verify(tuitionFeeSessionRepository, never()).saveAllAndFlush(anyList());
    }

    // ---- 4. Phiếu đã hủy / đã xóa không còn dòng session -> không xung đột ------------------------------

    @Test
    void generate_conflictQueryOnlyActiveFees_noRowsMeansNoConflict() {
        enrolled(student(8L, "HS01"));
        attendance(row(1, 8L, OCT_1));
        noConflicts();

        GenerateInvoicesResponseDto result = service.generateRange(request(OCT_1, OCT_31, "80000"));

        assertThat(result.getCreatedCount()).isEqualTo(1);
    }

    // ---- QC-1: > 1000 buổi, không IN-list ---------------------------------------------------------------

    @Test
    void generate_moreThan1000AttendanceRows_singleJoinConflictQuery_noIdList() {
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

        GenerateInvoicesResponseDto result = service.generateRange(request(from, to, "1000"));

        verify(tuitionFeeSessionRepository, times(1))
                .findActiveConflictsInClassRange(CLASS_ID, from, to, DomainConstants.BILLABLE_ATTENDANCE_STATUSES);
        assertThat(result.getCreatedCount()).isEqualTo(2);
        int saved = 0;
        for (List<TuitionFeeSessionEntity> batch : sessionBatches()) {
            saved += batch.size();
        }
        assertThat(saved).isEqualTo(1200);
    }

    // ---- 5. ORA-00001 -> 409 ---------------------------------------------------------------------------

    @Test
    void generate_uniqueViolationOnSessions_mapsToConflictWithEmptyDetails() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        attendance(row(1, 8L, OCT_1), row(2, 9L, OCT_1));
        noConflicts();
        when(tuitionFeeSessionRepository.saveAllAndFlush(anyList()))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(uniqueViolation());

        assertThatThrownBy(() -> service.generateRange(request(OCT_1, OCT_31, "80000")))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DomainConstants.FEE_SESSION_CONFLICT);
                    assertThat(((FeeSessionConflictDto.Payload) e.getData()).details()).isEmpty();
                });
    }

    @Test
    void generate_uniqueViolation_runsInOneTransaction_rolledBackNothingCommitted() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        attendance(row(1, 8L, OCT_1), row(2, 9L, OCT_1));
        noConflicts();
        when(tuitionFeeSessionRepository.saveAllAndFlush(anyList()))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(uniqueViolation());
        InMemoryTransactionManager tm = new InMemoryTransactionManager();
        ProxyFactory pf = new ProxyFactory(service);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        TuitionSlipService proxy = (TuitionSlipService) pf.getProxy();

        assertThatThrownBy(() -> proxy.generateRange(request(OCT_1, OCT_31, "80000")))
                .isInstanceOf(BusinessDataException.class);

        // Phiếu của học sinh thứ nhất đã saveAndFlush nhưng nằm cùng transaction -> rollback hết.
        assertThat(tm.begun).isEqualTo(1);
        assertThat(tm.commits).isZero();
        assertThat(tm.rollbacks).isEqualTo(1);
        assertThat(tm.depth()).isZero();
    }

    @Test
    void generate_success_singleTransactionCommitted() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        attendance(row(1, 8L, OCT_1), row(2, 9L, OCT_1));
        noConflicts();
        InMemoryTransactionManager tm = new InMemoryTransactionManager();
        ProxyFactory pf = new ProxyFactory(service);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));

        ((TuitionSlipService) pf.getProxy()).generateRange(request(OCT_1, OCT_31, "80000"));

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

        assertThatThrownBy(() -> service.generateRange(request(OCT_1, OCT_31, "80000")))
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

        assertThat(service.generateRange(request(OCT_1, oct15, "80000")).getCreatedCount()).isEqualTo(1);
        assertThat(service.generateRange(request(oct16, OCT_31, "80000")).getCreatedCount()).isEqualTo(1);

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
        GenerateRangeInvoicesRequestDto req = request(OCT_1, OCT_31, "1000");
        req.setClassId(999L);
        assertThatThrownBy(() -> service.generateRange(req))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("CLASS_NOT_FOUND");
    }

    // ---- Phạm vi học sinh (contract v1.3 mục 1) -------------------------------------------------------

    @Test
    void generate_billsInactiveStudentsWithSessions_skipsOnlyActiveWithZero() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        StudentEntity left = student(7L, "HS00");
        when(studentRepository.findById(7L)).thenReturn(Optional.of(left));
        attendance(row(1, 7L, OCT_1), row(2, 8L, OCT_1));
        noConflicts();

        GenerateInvoicesResponseDto result = service.generateRange(request(OCT_1, OCT_31, "1000"));

        assertThat(result.getCreatedCount()).isEqualTo(2);
        assertThat(result.getSkippedNoAttendance()).isEqualTo(1);
        assertThat(result.getFeeIds()).hasSize(result.getCreatedCount());
        assertThat(savedFees()).extracting(TuitionFeeEntity::getStudentId).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void generate_nothingToBill_returnsZeroCreated() {
        enrolled(student(8L, "HS01"));
        attendance();
        noConflicts();

        GenerateInvoicesResponseDto result = service.generateRange(request(OCT_1, OCT_31, "1000"));

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getFeeIds()).isEmpty();
        assertThat(result.getSkippedNoAttendance()).isEqualTo(1);
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
    }

    // ---- 11. Xem trước chỉ đọc, báo xung đột ----------------------------------------------------------

    @Test
    void preview_isReadOnly_reportsConflicts_inactive_andZeroSessionActive() {
        enrolled(student(8L, "HS01"), student(9L, "HS02"));
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student(7L, "HS00")));
        attendance(row(1, 7L, LocalDate.of(2026, 10, 2)), row(2, 8L, LocalDate.of(2026, 10, 9)),
                row(3, 8L, LocalDate.of(2026, 10, 2)));
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), eq(OCT_1), eq(OCT_31), any()))
                .thenReturn(List.of(new FeeSessionConflictRow(2L, 8L, LocalDate.of(2026, 10, 9), 50L, "HP-1")));

        BillableSessionsPreviewDto p = service.previewBillable(CLASS_ID, OCT_1, OCT_31, new BigDecimal("150000"));

        assertThat(p.getStudents()).extracting(BillableSessionsPreviewDto.StudentRow::getStudentCode)
                .containsExactly("HS00", "HS01", "HS02");
        BillableSessionsPreviewDto.StudentRow left = p.getStudents().get(0);
        assertThat(left.isInactive()).isTrue();
        BillableSessionsPreviewDto.StudentRow a = p.getStudents().get(1);
        assertThat(a.isInactive()).isFalse();
        assertThat(a.getSessionDates()).containsExactly(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 9));
        assertThat(a.getSessionCount()).isEqualTo(2);
        assertThat(a.getTotalAmount()).isEqualByComparingTo("300000");
        assertThat(a.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(a.getNetAmount()).isEqualByComparingTo("300000");
        assertThat(a.getConflictDates()).containsExactly(LocalDate.of(2026, 10, 9));
        assertThat(a.getConflictFeeCodes()).containsExactly("HP-1");
        BillableSessionsPreviewDto.StudentRow zero = p.getStudents().get(2);
        assertThat(zero.getSessionCount()).isZero();
        assertThat(p.getTotals().getStudentCount()).isEqualTo(3);
        assertThat(p.getTotals().getBillableStudentCount()).isEqualTo(2);
        assertThat(p.getTotals().getSkippedNoAttendance()).isEqualTo(1);
        assertThat(p.getTotals().getSessionCount()).isEqualTo(3);
        assertThat(p.getTotals().getTotalAmount()).isEqualByComparingTo("450000");
        assertThat(p.getTotals().getConflictStudentCount()).isEqualTo(1);
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
        verify(tuitionFeeRepository, never()).nextTuitionFeeCode();
        verify(tuitionFeeSessionRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void preview_inactiveWithZeroSessions_notShown_andNoPriceMeansNullMoney() {
        ClassStudentEntity dropped = ClassStudentEntity.builder().classId(CLASS_ID).studentId(7L)
                .status("DROPPED").isDeleted(0).build();
        StudentEntity a = student(8L, "HS01");
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(a));
        when(classStudentRepository.findByClassIdAndIsDeleted(CLASS_ID, 0)).thenReturn(List.of(dropped,
                ClassStudentEntity.builder().classId(CLASS_ID).studentId(8L).status("ENROLLED").isDeleted(0).build()));
        attendance(row(1, 8L, OCT_1));
        noConflicts();

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
        String html = service.generateSlipHtml(slip);
        assertThat(html).contains("Từ 01/10/2026 đến 31/10/2026").contains("02/10").doesNotContain("muộn");
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
