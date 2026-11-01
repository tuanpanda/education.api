package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.dto.request.GenerateRangeInvoicesRequestDto;
import com.education.base.dto.request.TuitionFeeCancelRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.FeeStudentErrorDto;
import com.education.base.dto.response.GenerateInvoicesResponseDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.entity.TuitionFeeSessionEntity;
import com.education.base.exception.BusinessDataException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.mapper.FinanceAcademicMapperImpl;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.repository.TuitionFeeSessionRepository;
import com.education.base.repository.projection.BillableAttendanceRow;
import com.education.base.repository.projection.FeeSessionConflictRow;
import com.education.base.service.BankAccountService;
import com.education.base.service.FileStorageService;
import com.education.base.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Contract fee-range-api v1.4 §13.4: buổi trên phiếu đã HỦY được tính là chưa thu lại. Dùng service thật cho cả
 * sinh phiếu ({@link TuitionSlipServiceImpl}) và hủy ({@link TuitionFeeServiceImpl}); bảng
 * {@code TUITION_FEE_SESSIONS} giả lập trong bộ nhớ, giữ ràng buộc {@code UQ_FEE_SESSIONS_ATTENDANCE}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeeRangeCancelRegenerateTest {

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
    @Mock
    PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    FileStorageService fileStorageService;
    @Mock
    PaymentService paymentService;

    TuitionSlipServiceImpl slips;
    TuitionFeeServiceImpl fees;

    final AtomicLong ids = new AtomicLong(500);
    final Map<Long, TuitionFeeEntity> feeTable = new HashMap<>();
    final List<TuitionFeeSessionEntity> sessionTable = new ArrayList<>();
    final List<BillableAttendanceRow> attendance = new ArrayList<>();

    @BeforeEach
    void setUp() {
        slips = new TuitionSlipServiceImpl(tuitionFeeRepository, tuitionFeeSessionRepository, attendanceRepository,
                classStudentRepository, classRepository, studentRepository, bankAccountService);
        fees = new TuitionFeeServiceImpl(tuitionFeeRepository, paymentTransactionRepository, studentRepository,
                classRepository, fileStorageService, new FileMapperImpl(), new FinanceAcademicMapperImpl(),
                bankAccountService, paymentService, tuitionFeeSessionRepository);

        when(bankAccountService.requireActive()).thenReturn(BankAccountResponseDto.builder()
                .bankBin("970436").accountNo("1234567890").accountName("TRUNG TAM").active(true).build());
        when(classRepository.findByIdAndIsDeleted(CLASS_ID, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(CLASS_ID).classCode("LH01").className("Lớp Sao Mai").build()));
        StudentEntity hs = StudentEntity.builder().id(8L).studentCode("HS01").fullName("Học sinh HS01")
                .status("ACTIVE").isDeleted(0).build();
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(hs));
        when(studentRepository.findById(8L)).thenReturn(Optional.of(hs));
        when(classStudentRepository.findByClassIdAndIsDeleted(CLASS_ID, 0)).thenReturn(List.of(
                ClassStudentEntity.builder().classId(CLASS_ID).studentId(8L).status("ENROLLED").isDeleted(0).build()));
        for (int i = 0; i < 12; i++) {
            attendance.add(new BillableAttendanceRow(100L + i, 8L, OCT_1.plusDays(i)));
        }
        when(attendanceRepository.findBillableInClassRange(eq(CLASS_ID), any(), any(), any()))
                .thenAnswer(inv -> List.copyOf(attendance));

        // ---- TUITION_FEES ----
        when(tuitionFeeRepository.nextTuitionFeeCode()).thenAnswer(inv -> "HP" + (ids.get() + 1));
        when(tuitionFeeRepository.saveAndFlush(any())).thenAnswer(inv -> {
            TuitionFeeEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(ids.incrementAndGet());
            }
            feeTable.put(e.getId(), e);
            return e;
        });
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(anyLong(), eq(0)))
                .thenAnswer(inv -> Optional.ofNullable(feeTable.get(inv.<Long>getArgument(0))));
        when(tuitionFeeRepository.findByIdAndIsDeleted(anyLong(), eq(0)))
                .thenAnswer(inv -> Optional.ofNullable(feeTable.get(inv.<Long>getArgument(0))));
        when(tuitionFeeRepository.getFeeDetail(anyLong())).thenAnswer(inv -> new TuitionFeeDetailResponse());
        when(fileStorageService.getFilesByRef(any(), any())).thenReturn(List.of());

        // ---- TUITION_FEE_SESSIONS (UQ_FEE_SESSIONS_ATTENDANCE) ----
        when(tuitionFeeSessionRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> {
            List<TuitionFeeSessionEntity> rows = inv.getArgument(0);
            Set<Long> held = new HashSet<>();
            sessionTable.forEach(s -> held.add(s.getAttendanceId()));
            for (TuitionFeeSessionEntity row : rows) {
                if (!held.add(row.getAttendanceId())) {
                    throw new DataIntegrityViolationException("could not execute statement", new RuntimeException(
                            "ORA-00001: unique constraint (EDUCATION.UQ_FEE_SESSIONS_ATTENDANCE) violated"));
                }
            }
            sessionTable.addAll(rows);
            return rows;
        });
        when(tuitionFeeSessionRepository.deleteByFeeId(anyLong())).thenAnswer(inv -> {
            Long feeId = inv.getArgument(0);
            int before = sessionTable.size();
            sessionTable.removeIf(s -> feeId.equals(s.getFeeId()));
            return before - sessionTable.size();
        });
        // Giống JPQL: chỉ phiếu chưa xóa, chưa hủy.
        when(tuitionFeeSessionRepository.findActiveConflictsInClassRange(eq(CLASS_ID), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<FeeSessionConflictRow> rows = new ArrayList<>();
                    for (TuitionFeeSessionEntity s : sessionTable) {
                        TuitionFeeEntity f = feeTable.get(s.getFeeId());
                        if (f != null && f.getIsDeleted() == 0
                                && !DomainConstants.FEE_STATUS_CANCELLED.equals(f.getStatus())) {
                            rows.add(new FeeSessionConflictRow(s.getAttendanceId(), f.getStudentId(),
                                    s.getSessionDate(), f.getId(), f.getFeeCode()));
                        }
                    }
                    return rows;
                });
    }

    @Test
    void cancelThenRegenerateSameRange_sameTenSessionsBilledAgain() {
        GenerateInvoicesResponseDto first = slips.generateRange(request());
        Long firstId = first.getFeeIds().getFirst();
        assertThat(sessionsOf(firstId)).containsExactly(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L);

        // Cùng khoảng, chưa hủy: còn 2 buổi chưa thu -> không đủ gói.
        assertThat(slips.previewBillable(CLASS_ID, OCT_1, OCT_31, null).getStudents().getFirst().getUnbilledCount())
                .isEqualTo(2);
        assertThatThrownBy(() -> slips.generateRange(request()))
                .isInstanceOfSatisfying(BusinessDataException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(DomainConstants.STUDENT_NOT_ELIGIBLE));

        fees.cancel(firstId, new TuitionFeeCancelRequest("Sai đơn giá"));
        assertThat(feeTable.get(firstId).getStatus()).isEqualTo("CANCELLED");
        assertThat(sessionsOf(firstId)).as("hủy giải phóng UQ ATTENDANCE_ID").isEmpty();
        assertThat(slips.previewBillable(CLASS_ID, OCT_1, OCT_31, null).getStudents().getFirst().getUnbilledCount())
                .isEqualTo(12);

        GenerateInvoicesResponseDto again = slips.generateRange(request());
        Long secondId = again.getFeeIds().getFirst();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(sessionsOf(secondId)).containsExactly(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L);
        assertThat(feeTable.get(secondId).getPeriodFrom()).isEqualTo(OCT_1);
        assertThat(feeTable.get(secondId).getPeriodTo()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(feeTable.get(firstId).getStatus()).as("phiếu cũ vẫn CANCELLED").isEqualTo("CANCELLED");
    }

    @Test
    void cancelledSlipSessions_doNotBlockEvenIfRowsWereNotReleased() {
        GenerateInvoicesResponseDto first = slips.generateRange(request());
        Long firstId = first.getFeeIds().getFirst();
        // Dữ liệu cũ: phiếu CANCELLED nhưng dòng session còn -> không tính là đã thu (JPQL lọc trạng thái).
        feeTable.get(firstId).setStatus("CANCELLED");

        assertThat(slips.previewBillable(CLASS_ID, OCT_1, OCT_31, null).getStudents().getFirst().getUnbilledCount())
                .isEqualTo(12);
        // Nhưng UQ vẫn giữ -> ghi bị chặn 409 (nên hủy luôn phải đi qua cancel/delete để giải phóng).
        assertThatThrownBy(() -> slips.generateRange(request()))
                .isInstanceOfSatisfying(BusinessDataException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(DomainConstants.FEE_SESSION_CONFLICT));
    }

    @Test
    void paidSlipCannotBeCancelled_sessionsStayBilled_regenerateRejected() {
        GenerateInvoicesResponseDto first = slips.generateRange(request());
        Long firstId = first.getFeeIds().getFirst();
        feeTable.get(firstId).setPaidAmount(new BigDecimal("1000"));
        feeTable.get(firstId).setStatus("PARTIAL");

        assertThatThrownBy(() -> fees.cancel(firstId, new TuitionFeeCancelRequest("x")))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_HAS_PAYMENTS");
        assertThat(sessionsOf(firstId)).hasSize(10);
        assertThatThrownBy(() -> slips.generateRange(request()))
                .isInstanceOfSatisfying(BusinessDataException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DomainConstants.STUDENT_NOT_ELIGIBLE);
                    FeeStudentErrorDto d = ((FeeStudentErrorDto.Payload) e.getData()).details()
                            .getFirst();
                    assertThat(d.getUnbilledCount()).isEqualTo(2);
                });
    }

    @Test
    void leftoverSessions_billedLaterAsPartial_overlappingRangeAllowed() {
        slips.generateRange(request());
        GenerateRangeInvoicesRequestDto rest = request();
        rest.getStudents().getFirst().setAllowPartial(true);
        rest.getStudents().getFirst().setPartialReason("Kết thúc khóa");

        GenerateInvoicesResponseDto second = slips.generateRange(rest);

        TuitionFeeEntity fee = feeTable.get(second.getFeeIds().getFirst());
        assertThat(sessionsOf(fee.getId())).containsExactly(110L, 111L);
        assertThat(fee.getIsPartial()).isEqualTo(1);
        assertThat(fee.getPeriodFrom()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(fee.getPeriodTo()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    private List<Long> sessionsOf(Long feeId) {
        return sessionTable.stream().filter(s -> feeId.equals(s.getFeeId()))
                .map(TuitionFeeSessionEntity::getAttendanceId).toList();
    }

    private static GenerateRangeInvoicesRequestDto request() {
        return GenerateRangeInvoicesRequestDto.builder().classId(CLASS_ID).fromDate(OCT_1).toDate(OCT_31)
                .pricePerSession(new BigDecimal("80000"))
                .students(new ArrayList<>(List.of(GenerateRangeInvoicesRequestDto.StudentSelection.builder()
                        .studentId(8L).build())))
                .build();
    }
}
