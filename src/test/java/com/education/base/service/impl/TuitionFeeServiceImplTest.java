package com.education.base.service.impl;

import com.education.base.dto.request.TuitionFeeCancelRequest;
import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.dto.request.TuitionFeeUpdateRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionFeeListItemDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.mapper.FinanceAcademicMapperImpl;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.BankAccountService;
import com.education.base.service.FileStorageService;
import com.education.base.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TuitionFeeServiceImplTest {

    @Mock
    private TuitionFeeRepository tuitionFeeRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private BankAccountService bankAccountService;
    @Mock
    private PaymentService paymentService;

    private TuitionFeeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TuitionFeeServiceImpl(tuitionFeeRepository, paymentTransactionRepository,
                studentRepository, classRepository, fileStorageService, new FileMapperImpl(),
                new FinanceAcademicMapperImpl(), bankAccountService, paymentService);
    }

    @Test
    void createQr_buildsEmvCoPayloadAndBase64Image() {
        when(tuitionFeeRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(studentRepository.findById(8L)).thenReturn(Optional.of(StudentEntity.builder()
                .id(8L).studentCode("SV01").fullName("Nguyen Van A").build()));
        when(bankAccountService.requireActive()).thenReturn(activeAccount());

        TuitionQrResponseDto qr = service.createQr(4L, new TuitionQrRequest());

        assertThat(qr.getTuitionFeeId()).isEqualTo(4L);
        assertThat(qr.getRemainingAmount()).isEqualByComparingTo("1500000");
        assertThat(qr.getQrPayload()).startsWith("000201");
        assertThat(qr.getQrPayload()).contains("6304");
        assertThat(qr.getBase64Image()).startsWith("data:image/png;base64,");
        assertThat(qr.getQuickUrl()).contains("970436-1234567890");
    }

    @Test
    void search_batchLoadsStudentsAndClassesInsteadOfPerRowLookups() {
        TuitionFeeEntity a = unpaidFee();
        TuitionFeeEntity b = unpaidFee();
        b.setId(5L);
        b.setFeeCode("FEE02");
        b.setClassId(70L);
        TuitionFeeEntity c = unpaidFee();
        c.setId(6L);
        c.setFeeCode("FEE03");
        c.setStudentId(9L);
        c.setClassId(70L);
        when(tuitionFeeRepository.findAll(org.mockito.ArgumentMatchers.<Specification<TuitionFeeEntity>>any(),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(a, b, c)));
        when(studentRepository.findAllById(any())).thenReturn(List.of(
                StudentEntity.builder().id(8L).studentCode("SV01").fullName("Nguyen Van A").build(),
                StudentEntity.builder().id(9L).studentCode("SV02").fullName("Tran Thi B")
                        .status("INACTIVE").build()));
        when(classRepository.findAllById(any())).thenReturn(List.of(
                ClassEntity.builder().id(70L).classCode("L01").className("Lop 1").build()));

        PageResponse<TuitionFeeListItemDto> page = service.search(new TuitionFeeFilterRequest());

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent().get(0).getStudentCode()).isEqualTo("SV01");
        assertThat(page.getContent().get(0).getClassCode()).isNull();
        assertThat(page.getContent().get(1).getClassName()).isEqualTo("Lop 1");
        assertThat(page.getContent().get(2).getStudentName()).isEqualTo("Tran Thi B");
        // B8: trạng thái học sinh đi kèm từng dòng để UI gắn nhãn "Đã nghỉ".
        assertThat(page.getContent().get(2).getStudentStatus()).isEqualTo("INACTIVE");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Long>> studentIds = ArgumentCaptor.forClass(Iterable.class);
        verify(studentRepository, times(1)).findAllById(studentIds.capture());
        assertThat(studentIds.getValue()).containsExactlyInAnyOrder(8L, 9L);
        verify(classRepository, times(1)).findAllById(any());
        verify(studentRepository, never()).findById(any());
        verify(classRepository, never()).findById(any());
    }

    // ---- B6: createQr theo số còn phải thu, chặn PAID / CANCELLED / hết nợ ---------------------------

    @Test
    void createQr_partialFee_usesRemainingAmountAsQrAmount() {
        TuitionFeeEntity fee = unpaidFee();
        fee.setPaidAmount(new BigDecimal("300000"));
        fee.setStatus("PARTIAL");
        when(tuitionFeeRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.of(fee));
        when(studentRepository.findById(8L)).thenReturn(Optional.empty());
        when(bankAccountService.requireActive()).thenReturn(activeAccount());

        TuitionQrResponseDto qr = service.createQr(4L, new TuitionQrRequest());

        assertThat(qr.getRemainingAmount()).isEqualByComparingTo("1200000");
        // Tag 54 (Transaction Amount) = 1200000, độ dài 07.
        assertThat(qr.getQrPayload()).contains("54071200000");
    }

    @Test
    void createQr_rejectsPaidCancelledAndZeroRemaining() {
        TuitionFeeEntity paid = unpaidFee();
        paid.setPaidAmount(new BigDecimal("1500000"));
        paid.setStatus("PAID");
        TuitionFeeEntity cancelled = unpaidFee();
        cancelled.setId(5L);
        cancelled.setStatus("CANCELLED");
        TuitionFeeEntity zeroRemaining = unpaidFee();
        zeroRemaining.setId(6L);
        zeroRemaining.setDiscountAmount(new BigDecimal("2000000"));
        when(tuitionFeeRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.of(paid));
        when(tuitionFeeRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(cancelled));
        when(tuitionFeeRepository.findByIdAndIsDeleted(6L, 0)).thenReturn(Optional.of(zeroRemaining));

        assertThatThrownBy(() -> service.createQr(4L, new TuitionQrRequest()))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_ALREADY_PAID");
        assertThatThrownBy(() -> service.createQr(5L, new TuitionQrRequest()))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_CANCELLED");
        assertThatThrownBy(() -> service.createQr(6L, new TuitionQrRequest()))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_ALREADY_PAID");
        verify(bankAccountService, never()).requireActive();
    }

    // ---- Tạo: mã khoản phí tùy chọn ---------------------------------------------------------------

    @Test
    void create_blankFeeCode_generatesCodeFromRule() {
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(
                StudentEntity.builder().id(8L).status("ACTIVE").build()));
        when(tuitionFeeRepository.nextTuitionFeeCode()).thenReturn("HP2026100001");
        when(tuitionFeeRepository.saveAndFlush(any(TuitionFeeEntity.class))).thenAnswer(inv -> {
            TuitionFeeEntity e = inv.getArgument(0);
            e.setId(40L);
            return e;
        });
        stubDetail(40L);

        service.create(createRequest("  "));

        ArgumentCaptor<TuitionFeeEntity> saved = ArgumentCaptor.forClass(TuitionFeeEntity.class);
        verify(tuitionFeeRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFeeCode()).isEqualTo("HP2026100001");
        assertThat(saved.getValue().getStatus()).isEqualTo("UNPAID");
        verify(tuitionFeeRepository, never()).existsByFeeCode(any());
    }

    @Test
    void create_explicitFeeCode_isKeptAndCheckedForDuplicates() {
        when(tuitionFeeRepository.existsByFeeCode("FEE-X")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(" FEE-X ")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("FEE_CODE_DUPLICATED");
        verify(tuitionFeeRepository, never()).nextTuitionFeeCode();
    }

    // ---- Cập nhật ------------------------------------------------------------------------------------

    @Test
    void update_replacesEditableFieldsAndRecomputesStatus() {
        TuitionFeeEntity fee = unpaidFee();
        fee.setPaidAmount(new BigDecimal("500000"));
        fee.setStatus("PARTIAL");
        fee.setNote("cũ");
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(fee));
        stubDetail(4L);

        LocalDate due = LocalDate.now().plusDays(10);
        service.update(4L, TuitionFeeUpdateRequest.builder()
                .totalAmount(new BigDecimal("600000")).discountAmount(new BigDecimal("100000"))
                .dueDate(due).note("  ").build());

        assertThat(fee.getTotalAmount()).isEqualByComparingTo("600000");
        assertThat(fee.getDiscountAmount()).isEqualByComparingTo("100000");
        assertThat(fee.getPaidAmount()).isEqualByComparingTo("500000");
        assertThat(fee.getDueDate()).isEqualTo(due);
        assertThat(fee.getNote()).isNull();
        assertThat(fee.getStatus()).isEqualTo("PAID");
        assertThat(fee.getUpdatedBy()).isEqualTo("SYSTEM");
        verify(tuitionFeeRepository).saveAndFlush(fee);
    }

    @Test
    void update_nullDiscountMeansZero() {
        TuitionFeeEntity fee = unpaidFee();
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(fee));
        stubDetail(4L);

        service.update(4L, TuitionFeeUpdateRequest.builder().totalAmount(new BigDecimal("900000")).build());

        assertThat(fee.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(fee.getStatus()).isEqualTo("UNPAID");
    }

    @Test
    void update_rejectsCancelledDiscountAboveTotalAndNetBelowPaid() {
        TuitionFeeEntity cancelled = unpaidFee();
        cancelled.setStatus("CANCELLED");
        TuitionFeeEntity partial = unpaidFee();
        partial.setId(5L);
        partial.setPaidAmount(new BigDecimal("800000"));
        partial.setStatus("PARTIAL");
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(cancelled));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(5L, 0)).thenReturn(Optional.of(partial));

        assertThatThrownBy(() -> service.update(4L, TuitionFeeUpdateRequest.builder()
                .totalAmount(new BigDecimal("1000000")).build()))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_CANCELLED");
        assertThatThrownBy(() -> service.update(5L, TuitionFeeUpdateRequest.builder()
                .totalAmount(new BigDecimal("1000000")).discountAmount(new BigDecimal("1000001")).build()))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("INVALID_DISCOUNT");
        assertThatThrownBy(() -> service.update(5L, TuitionFeeUpdateRequest.builder()
                .totalAmount(new BigDecimal("1000000")).discountAmount(new BigDecimal("300000")).build()))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_TOTAL_BELOW_PAID");
        assertThat(partial.getTotalAmount()).isEqualByComparingTo("2000000");
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
    }

    // ---- Hủy ------------------------------------------------------------------------------------------

    @Test
    void cancel_unpaidFee_setsCancelledWithReason() {
        TuitionFeeEntity fee = unpaidFee();
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(fee));
        stubDetail(fee);
        when(studentRepository.findById(8L)).thenReturn(Optional.of(
                StudentEntity.builder().id(8L).status("INACTIVE").build()));

        TuitionFeeDetailResponse detail = service.cancel(4L, new TuitionFeeCancelRequest("  Học sinh nghỉ  "));

        assertThat(fee.getStatus()).isEqualTo("CANCELLED");
        assertThat(fee.getCancelReason()).isEqualTo("Học sinh nghỉ");
        assertThat(detail.getCancelReason()).isEqualTo("Học sinh nghỉ");
        assertThat(detail.getStudentStatus()).isEqualTo("INACTIVE");
        verify(tuitionFeeRepository).saveAndFlush(fee);
    }

    @Test
    void cancel_rejectsFeeWithPaymentsOrAlreadyCancelledOrBlankReason() {
        TuitionFeeEntity partial = unpaidFee();
        partial.setPaidAmount(new BigDecimal("1"));
        partial.setStatus("PARTIAL");
        TuitionFeeEntity cancelled = unpaidFee();
        cancelled.setId(5L);
        cancelled.setStatus("CANCELLED");
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(partial));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(5L, 0)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.cancel(4L, new TuitionFeeCancelRequest("x")))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_HAS_PAYMENTS");
        assertThatThrownBy(() -> service.cancel(5L, new TuitionFeeCancelRequest("x")))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_CANCELLED");
        assertThatThrownBy(() -> service.cancel(4L, new TuitionFeeCancelRequest(" ")))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("CANCEL_REASON_REQUIRED");
        assertThat(partial.getStatus()).isEqualTo("PARTIAL");
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
    }

    // ---- Xóa ------------------------------------------------------------------------------------------

    @Test
    void delete_unpaidFeeWithoutTransactions_softDeletes() {
        TuitionFeeEntity fee = unpaidFee();
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(fee));
        when(paymentTransactionRepository.findByTuitionFeeIdAndIsDeleted(4L, 0)).thenReturn(List.of());

        service.delete(4L);

        assertThat(fee.getIsDeleted()).isEqualTo(1);
        verify(tuitionFeeRepository).saveAndFlush(fee);
    }

    @Test
    void delete_rejectsNonUnpaidOrFeeWithTransactions() {
        TuitionFeeEntity overdue = unpaidFee();
        overdue.setStatus("OVERDUE");
        TuitionFeeEntity withVoidedTxn = unpaidFee();
        withVoidedTxn.setId(5L);
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(overdue));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(5L, 0)).thenReturn(Optional.of(withVoidedTxn));
        when(paymentTransactionRepository.findByTuitionFeeIdAndIsDeleted(5L, 0))
                .thenReturn(List.of(PaymentTransactionEntity.builder().id(1L).build()));

        assertThatThrownBy(() -> service.delete(4L))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_NOT_DELETABLE");
        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_NOT_DELETABLE");
        assertThat(withVoidedTxn.getIsDeleted()).isZero();
        verify(tuitionFeeRepository, never()).saveAndFlush(any());
    }

    @Test
    void lifecycle_missingFee_returnsFeeNotFound() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(99L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FEE_NOT_FOUND");
    }

    private static TuitionFeeCreateRequest createRequest(String feeCode) {
        TuitionFeeCreateRequest request = new TuitionFeeCreateRequest();
        request.setFeeCode(feeCode);
        request.setStudentId(8L);
        request.setTotalAmount(new BigDecimal("1000000"));
        return request;
    }

    @Test
    void getDetail_recomputesRemainingNeverNegative() {
        TuitionFeeEntity stored = unpaidFee();
        stored.setId(4L);
        when(tuitionFeeRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.of(stored));
        when(tuitionFeeRepository.getFeeDetail(4L)).thenReturn(TuitionFeeDetailResponse.builder()
                .id(4L).totalAmount(new BigDecimal("1000000")).discountAmount(new BigDecimal("100000"))
                .paidAmount(new BigDecimal("950000")).remainingAmount(new BigDecimal("-50000")).build());
        when(fileStorageService.getFilesByRef(any(), any())).thenReturn(List.of());

        assertThat(service.getDetail(4L).getRemainingAmount()).isEqualByComparingTo("0");
    }

    private void stubDetail(Long id) {
        TuitionFeeEntity stored = unpaidFee();
        stored.setId(id);
        stubDetail(stored);
    }

    /** Cùng persistence context: getDetail đọc lại đúng entity vừa sửa. */
    private void stubDetail(TuitionFeeEntity stored) {
        Long id = stored.getId();
        when(tuitionFeeRepository.findByIdAndIsDeleted(id, 0)).thenAnswer(inv -> Optional.of(stored));
        when(tuitionFeeRepository.getFeeDetail(id)).thenReturn(new TuitionFeeDetailResponse());
        when(fileStorageService.getFilesByRef(any(), any())).thenReturn(List.of());
    }

    private BankAccountResponseDto activeAccount() {
        return BankAccountResponseDto.builder()
                .id(1L)
                .bankBin("970436")
                .bankName("Vietcombank")
                .accountNo("1234567890")
                .accountName("TRUONG EDUCATION")
                .active(true)
                .build();
    }

    private TuitionFeeEntity unpaidFee() {
        return TuitionFeeEntity.builder()
                .id(4L)
                .feeCode("FEE01")
                .studentId(8L)
                .totalAmount(new BigDecimal("2000000"))
                .discountAmount(new BigDecimal("500000"))
                .paidAmount(BigDecimal.ZERO)
                .status("UNPAID")
                .isDeleted(0)
                .build();
    }
}
