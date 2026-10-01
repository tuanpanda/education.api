package com.education.base.service.impl;

import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.mapper.FinanceAcademicMapperImpl;
import com.education.base.mapper.PaymentTransactionMapperImpl;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.BankAccountService;
import com.education.base.service.FileStorageService;
import com.education.base.service.PaymentService;
import com.education.base.common.DomainConstants;
import com.education.base.dto.request.PaymentTransactionFilterRequest;
import com.education.base.dto.request.RefundTransactionRequest;
import com.education.base.dto.request.VoidTransactionRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDetailResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.UserEntity;
import com.education.base.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private TuitionFeeRepository tuitionFeeRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private BankAccountService bankAccountService;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(tuitionFeeRepository, paymentTransactionRepository,
                bankAccountService, new PaymentTransactionMapperImpl(), studentRepository, classRepository,
                userRepository, jdbcTemplate);
    }

    // ---- confirmPayment: chuyển từ TuitionFeeServiceImplTest (B-1); sau B-1 chỉ thêm stub cấp số phiếu ----

    @Test
    void confirmPayment_updatesPaidAmountAndStatusInSameSave() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(bankAccountService.requireActive()).thenReturn(activeAccount());
        stubReceiptNo("PT20261000001");
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity entity = invocation.getArgument(0);
            entity.setId(33L);
            return entity;
        });
        when(tuitionFeeRepository.save(any(TuitionFeeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        request.setAmount(new BigDecimal("1500000"));
        request.setPaymentMethod("VIETQR");
        request.setBankReferenceNo("REF-001");

        PaymentTransactionDto dto = service.confirmPayment(4L, request);
        assertThat(dto.getAmount()).isEqualByComparingTo("1500000");
        assertThat(dto.getStatus()).isEqualTo("SUCCESS");

        ArgumentCaptor<TuitionFeeEntity> captor = ArgumentCaptor.forClass(TuitionFeeEntity.class);
        verify(tuitionFeeRepository).save(captor.capture());
        assertThat(captor.getValue().getPaidAmount()).isEqualByComparingTo("1500000");
        assertThat(captor.getValue().getStatus()).isEqualTo("PAID");
        // Khoản phí phải được nạp bằng truy vấn khóa dòng, không dùng truy vấn thường.
        verify(tuitionFeeRepository, never()).findByIdAndIsDeleted(any(), any());
    }


    @Test
    void confirmPayment_duplicateBankReference_rejectedWithFriendlyMessage() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(paymentTransactionRepository.existsByBankReferenceNo("FT2609300001")).thenReturn(true);

        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        request.setAmount(new BigDecimal("500000"));
        request.setBankReferenceNo("  FT2609300001  ");

        assertThatThrownBy(() -> service.confirmPayment(4L, request))
                .isInstanceOf(OracleBusinessException.class)
                .satisfies(ex -> {
                    OracleBusinessException be = (OracleBusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("BANK_REFERENCE_DUPLICATED");
                    assertThat(be.getMessage()).contains("FT2609300001").contains("đã được ghi nhận");
                });
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
        verify(tuitionFeeRepository, never()).save(any());
    }


    @Test
    void confirmPayment_concurrentDuplicateBankReference_uniqueIndexViolationIsTranslated() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(paymentTransactionRepository.existsByBankReferenceNo("FT2609300002")).thenReturn(false);
        when(bankAccountService.requireActive()).thenReturn(activeAccount());
        stubReceiptNo("PT20261000001");
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransactionEntity.class))).thenThrow(
                new DataIntegrityViolationException("could not execute statement",
                        new SQLIntegrityConstraintViolationException(
                                "ORA-00001: unique constraint (EDUCATION.UQ_FIN_TRANS_BANK_REF) violated")));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        request.setAmount(new BigDecimal("500000"));
        request.setBankReferenceNo("FT2609300002");

        assertThatThrownBy(() -> service.confirmPayment(4L, request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("BANK_REFERENCE_DUPLICATED");
        verify(tuitionFeeRepository, never()).save(any());
    }


    @Test
    void confirmPayment_otherIntegrityViolation_isNotMisreportedAsDuplicateReference() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(bankAccountService.requireActive()).thenReturn(activeAccount());
        stubReceiptNo("PT20261000001");
        DataIntegrityViolationException violation = new DataIntegrityViolationException("x",
                new SQLIntegrityConstraintViolationException("ORA-02290: check constraint (EDUCATION.CK_TRANS_METHOD) violated"));
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransactionEntity.class))).thenThrow(violation);

        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        request.setAmount(new BigDecimal("500000"));
        request.setBankReferenceNo("FT2609300003");

        assertThatThrownBy(() -> service.confirmPayment(4L, request)).isSameAs(violation);
    }


    @Test
    void confirmPayment_rowLockTimeout_returnsRetryMessage() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0))
                .thenThrow(new PessimisticLockingFailureException("ORA-30006: resource busy"));

        assertThatThrownBy(() -> service.confirmPayment(4L, new ConfirmPaymentRequest()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("FEE_LOCKED");
    }


    @Test
    void confirmPayment_alreadyPaid_throws() {
        TuitionFeeEntity fee = unpaidFee();
        fee.setPaidAmount(new BigDecimal("2000000"));
        fee.setStatus("PAID");
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(fee));

        assertThatThrownBy(() -> service.confirmPayment(4L, new ConfirmPaymentRequest()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("FEE_ALREADY_PAID");
    }

    @Test
    void tuitionFeeService_confirmPayment_delegatesToPaymentService() {
        PaymentService paymentService = mock(PaymentService.class);
        TuitionFeeServiceImpl tuitionFeeService = new TuitionFeeServiceImpl(tuitionFeeRepository,
                paymentTransactionRepository, mock(StudentRepository.class), mock(ClassRepository.class),
                mock(FileStorageService.class), new FileMapperImpl(), new FinanceAcademicMapperImpl(),
                bankAccountService, paymentService);
        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        PaymentTransactionDto expected = PaymentTransactionDto.builder().id(33L).status("SUCCESS").build();
        when(paymentService.confirmPayment(4L, request)).thenReturn(expected);

        assertThat(tuitionFeeService.confirmPayment(4L, request)).isSameAs(expected);
        verify(tuitionFeeRepository, never()).findByIdAndIsDeletedForUpdate(any(), any());
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
    }

    // ---- confirmPayment: phiếu thu (V14_2) ------------------------------------------------------

    @Test
    void confirmPayment_assignsReceiptNoTypeDefaultPayerAndCashier() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(bankAccountService.requireActive()).thenReturn(activeAccount());
        when(studentRepository.findById(8L)).thenReturn(Optional.of(StudentEntity.builder()
                .id(8L).studentCode("SV01").fullName("Nguyen Van A").parentName("Nguyen Van Bo").build()));
        stubReceiptNo("PT20261000007");
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity entity = invocation.getArgument(0);
            entity.setId(34L);
            return entity;
        });

        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        request.setAmount(new BigDecimal("500000"));
        request.setPaymentMethod("cash");

        PaymentTransactionDto dto = service.confirmPayment(4L, request);

        assertThat(dto.getReceiptNo()).isEqualTo("PT20261000007");
        assertThat(dto.getTransactionType()).isEqualTo("PAYMENT");
        assertThat(dto.getPayerName()).isEqualTo("Nguyen Van Bo");
        assertThat(dto.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(dto.getPaymentMethod()).isEqualTo("CASH");
        verify(jdbcTemplate).execute(eq(PaymentServiceImpl.NEXT_RECEIPT_NO_CALL),
                ArgumentMatchers.<CallableStatementCallback<String>>any());
    }

    @Test
    void confirmPayment_explicitPayerName_winsOverStudentDefault() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(bankAccountService.requireActive()).thenReturn(activeAccount());
        stubReceiptNo("PT20261000008");
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        request.setAmount(new BigDecimal("500000"));
        request.setPayerName("  Tran Thi Me  ");

        assertThat(service.confirmPayment(4L, request).getPayerName()).isEqualTo("Tran Thi Me");
    }

    @Test
    void confirmPayment_receiptRuleMissing_failsBeforeInsert() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(bankAccountService.requireActive()).thenReturn(activeAccount());
        stubReceiptNo(null);

        assertThatThrownBy(() -> service.confirmPayment(4L, new ConfirmPaymentRequest()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("RECEIPT_NO_GENERATE_FAILED");
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
        verify(tuitionFeeRepository, never()).save(any());
    }

    // ---- Hủy giao dịch -------------------------------------------------------------------------------

    @Test
    void void_marksVoidedAndRecomputesPaidAmountAndStatus() {
        TuitionFeeEntity fee = unpaidFee();
        fee.setPaidAmount(new BigDecimal("1500000"));
        fee.setStatus("PAID");
        PaymentTransactionEntity first = payment(50L, "1000000", "SUCCESS");
        PaymentTransactionEntity second = payment(51L, "500000", "SUCCESS");
        stubLockedFee(50L, fee);
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(first));
        when(paymentTransactionRepository.save(any(PaymentTransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.findByTuitionFeeIdAndIsDeleted(4L, 0)).thenReturn(List.of(first, second));

        PaymentTransactionDto dto = service.voidTransaction(50L, new VoidTransactionRequest("  Nhập nhầm  "));

        assertThat(dto.getStatus()).isEqualTo("VOIDED");
        assertThat(dto.getVoidReason()).isEqualTo("Nhập nhầm");
        assertThat(dto.getVoidedBy()).isEqualTo("SYSTEM");
        assertThat(dto.getVoidedAt()).isNotNull();
        ArgumentCaptor<TuitionFeeEntity> captor = ArgumentCaptor.forClass(TuitionFeeEntity.class);
        verify(tuitionFeeRepository).save(captor.capture());
        assertThat(captor.getValue().getPaidAmount()).isEqualByComparingTo("500000");
        assertThat(captor.getValue().getStatus()).isEqualTo("PARTIAL");
        // Khóa khoản phí bằng truy vấn FOR UPDATE, không dùng truy vấn thường.
        verify(tuitionFeeRepository).findByIdAndIsDeletedForUpdate(4L, 0);
        verify(tuitionFeeRepository, never()).findByIdAndIsDeleted(any(), any());
    }

    @Test
    void void_cancelledFee_keepsCancelledStatus() {
        TuitionFeeEntity fee = unpaidFee();
        fee.setPaidAmount(new BigDecimal("500000"));
        fee.setStatus("CANCELLED");
        PaymentTransactionEntity tx = payment(50L, "500000", "SUCCESS");
        stubLockedFee(50L, fee);
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(tx));
        when(paymentTransactionRepository.save(any(PaymentTransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.findByTuitionFeeIdAndIsDeleted(4L, 0)).thenReturn(List.of(tx));

        service.voidTransaction(50L, new VoidTransactionRequest("Hủy lớp"));

        assertThat(fee.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(fee.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void void_twice_isRejected() {
        PaymentTransactionEntity tx = payment(50L, "500000", "VOIDED");
        tx.setVoidedAt(LocalDateTime.now());
        stubLockedFee(50L, unpaidFee());
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> service.voidTransaction(50L, new VoidTransactionRequest("lần 2")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_ALREADY_VOIDED");
        verify(paymentTransactionRepository, never()).save(any());
        verify(tuitionFeeRepository, never()).save(any());
    }

    @Test
    void void_afterRefund_isRejected() {
        PaymentTransactionEntity tx = payment(50L, "1000000", "SUCCESS");
        stubLockedFee(50L, unpaidFee());
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(tx));
        when(paymentTransactionRepository.findByRefTransactionIdAndIsDeletedOrderByPaymentDateDescIdDesc(50L, 0))
                .thenReturn(List.of(refund(60L, 50L, "200000")));

        assertThatThrownBy(() -> service.voidTransaction(50L, new VoidTransactionRequest("sai")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_HAS_REFUNDS");
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void void_refundRow_isRejected() {
        stubLockedFee(60L, unpaidFee());
        when(paymentTransactionRepository.findByIdAndIsDeleted(60L, 0)).thenReturn(Optional.of(refund(60L, 50L, "1")));

        assertThatThrownBy(() -> service.voidTransaction(60L, new VoidTransactionRequest("x")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_VOIDABLE");
    }

    @Test
    void void_blankReason_isRejectedBeforeLocking() {
        assertThatThrownBy(() -> service.voidTransaction(50L, new VoidTransactionRequest("   ")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("VOID_REASON_REQUIRED");
        verify(tuitionFeeRepository, never()).findByIdAndIsDeletedForUpdate(any(), any());
    }

    @Test
    void void_rowLockTimeout_returnsRetryMessage() {
        when(paymentTransactionRepository.findTuitionFeeIdByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(4L));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0))
                .thenThrow(new PessimisticLockingFailureException("ORA-30006: resource busy"));

        assertThatThrownBy(() -> service.voidTransaction(50L, new VoidTransactionRequest("x")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("FEE_LOCKED");
    }

    @Test
    void void_unknownTransaction_notFound() {
        when(paymentTransactionRepository.findTuitionFeeIdByIdAndIsDeleted(99L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.voidTransaction(99L, new VoidTransactionRequest("x")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_FOUND");
    }

    // ---- Hoàn tiền --------------------------------------------------------------------------------

    @Test
    void refund_partial_createsRefundRowAndRecomputesFee() {
        TuitionFeeEntity fee = unpaidFee();
        fee.setPaidAmount(new BigDecimal("1500000"));
        fee.setStatus("PAID");
        PaymentTransactionEntity original = payment(50L, "1500000", "SUCCESS");
        original.setPayerName("Nguyen Van Bo");
        stubLockedFee(50L, fee);
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(original));
        stubReceiptNo("PT20261000009");
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity entity = invocation.getArgument(0);
            entity.setId(61L);
            return entity;
        });
        when(paymentTransactionRepository.findByTuitionFeeIdAndIsDeleted(4L, 0)).thenReturn(List.of(original));

        PaymentTransactionDto dto = service.refundTransaction(50L,
                new RefundTransactionRequest(new BigDecimal("400000"), "cash", "Nghỉ học 2 buổi", null));

        assertThat(dto.getTransactionType()).isEqualTo("REFUND");
        assertThat(dto.getStatus()).isEqualTo("SUCCESS");
        assertThat(dto.getRefTransactionId()).isEqualTo(50L);
        assertThat(dto.getAmount()).isEqualByComparingTo("400000");
        assertThat(dto.getReceiptNo()).isEqualTo("PT20261000009");
        assertThat(dto.getTransactionCode()).startsWith("RFD50");
        assertThat(dto.getPaymentMethod()).isEqualTo("CASH");
        assertThat(dto.getPayerName()).isEqualTo("Nguyen Van Bo");
        assertThat(dto.getNote()).isEqualTo("Nghỉ học 2 buổi");
        assertThat(original.getStatus()).isEqualTo("SUCCESS");
        verify(paymentTransactionRepository, never()).save(any(PaymentTransactionEntity.class));
        assertThat(fee.getPaidAmount()).isEqualByComparingTo("1100000");
        assertThat(fee.getStatus()).isEqualTo("PARTIAL");
        verify(tuitionFeeRepository).save(fee);
        verify(tuitionFeeRepository).findByIdAndIsDeletedForUpdate(4L, 0);
    }

    @Test
    void refund_remainingAmount_marksOriginalRefunded() {
        TuitionFeeEntity fee = unpaidFee();
        fee.setPaidAmount(new BigDecimal("1000000"));
        fee.setStatus("PARTIAL");
        PaymentTransactionEntity original = payment(50L, "1000000", "SUCCESS");
        PaymentTransactionEntity earlier = refund(60L, 50L, "300000");
        PaymentTransactionEntity voidedRefund = refund(62L, 50L, "999999");
        voidedRefund.setStatus("VOIDED");
        stubLockedFee(50L, fee);
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(original));
        when(paymentTransactionRepository.findByRefTransactionIdAndIsDeletedOrderByPaymentDateDescIdDesc(50L, 0))
                .thenReturn(List.of(earlier, voidedRefund));
        stubReceiptNo("PT20261000010");
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity entity = invocation.getArgument(0);
            entity.setId(63L);
            return entity;
        });
        when(paymentTransactionRepository.save(any(PaymentTransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.findByTuitionFeeIdAndIsDeleted(4L, 0))
                .thenReturn(List.of(original, earlier, voidedRefund));

        service.refundTransaction(50L,
                new RefundTransactionRequest(new BigDecimal("700000"), "BANK_TRANSFER", "Chuyển trung tâm", "Me"));

        assertThat(original.getStatus()).isEqualTo("REFUNDED");
        verify(paymentTransactionRepository).save(original);
        // 1.000.000 (REFUNDED vẫn là tiền đã thu) - 300.000 - 700.000 = 0; dòng hoàn đã hủy không tính.
        assertThat(fee.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(fee.getStatus()).isEqualTo("UNPAID");
    }

    @Test
    void refund_overRefundable_isRejected() {
        PaymentTransactionEntity original = payment(50L, "1000000", "SUCCESS");
        stubLockedFee(50L, unpaidFee());
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(original));
        when(paymentTransactionRepository.findByRefTransactionIdAndIsDeletedOrderByPaymentDateDescIdDesc(50L, 0))
                .thenReturn(List.of(refund(60L, 50L, "600000")));

        assertThatThrownBy(() -> service.refundTransaction(50L,
                new RefundTransactionRequest(new BigDecimal("400001"), "CASH", "quá", null)))
                .isInstanceOf(OracleBusinessException.class)
                .satisfies(ex -> {
                    OracleBusinessException be = (OracleBusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("REFUND_EXCEEDS_REFUNDABLE");
                    assertThat(be.getMessage()).contains("400000");
                });
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
        verify(tuitionFeeRepository, never()).save(any());
    }

    @Test
    void refund_voidedOrFullyRefundedPayment_isRejected() {
        stubLockedFee(50L, unpaidFee());
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0))
                .thenReturn(Optional.of(payment(50L, "1000000", "VOIDED")));

        assertThatThrownBy(() -> service.refundTransaction(50L,
                new RefundTransactionRequest(new BigDecimal("1"), "CASH", "x", null)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_REFUNDABLE");

        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0))
                .thenReturn(Optional.of(payment(50L, "1000000", "REFUNDED")));
        assertThatThrownBy(() -> service.refundTransaction(50L,
                new RefundTransactionRequest(new BigDecimal("1"), "CASH", "x", null)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_REFUNDABLE");
    }

    @Test
    void refund_nonPositiveAmount_isRejectedBeforeLocking() {
        assertThatThrownBy(() -> service.refundTransaction(50L,
                new RefundTransactionRequest(BigDecimal.ZERO, "CASH", "x", null)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_REFUND_AMOUNT");
        verify(tuitionFeeRepository, never()).findByIdAndIsDeletedForUpdate(any(), any());
    }

    @Test
    void netPaid_countsSuccessAndRefundedPayments_minusActiveRefunds() {
        PaymentTransactionEntity legacy = payment(1L, "100", "SUCCESS");
        legacy.setTransactionType(null);
        PaymentTransactionEntity deleted = payment(6L, "7777", "SUCCESS");
        deleted.setIsDeleted(1);
        PaymentTransactionEntity voidedRefund = refund(5L, 2L, "40");
        voidedRefund.setStatus("VOIDED");
        List<PaymentTransactionEntity> rows = new ArrayList<>(List.of(
                legacy,
                payment(2L, "200", "REFUNDED"),
                payment(3L, "300", "VOIDED"),
                payment(4L, "400", "PENDING"),
                refund(7L, 2L, "200"),
                voidedRefund,
                deleted));

        assertThat(PaymentServiceImpl.netPaid(rows)).isEqualByComparingTo("100");
        rows.add(refund(8L, 1L, "500"));
        assertThat(PaymentServiceImpl.netPaid(rows)).isEqualByComparingTo("0");
    }

    // ---- Tra cứu / chi tiết / phiếu thu ----------------------------------------------------------

    @Test
    void search_sortsByPaymentDateDescAndEnrichesRowsInBatch() {
        PaymentTransactionEntity pay = payment(50L, "1000000", "SUCCESS");
        PaymentTransactionEntity ref = refund(60L, 50L, "250000");
        when(paymentTransactionRepository.findAll(ArgumentMatchers.<Specification<PaymentTransactionEntity>>any(),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ref, pay)));
        TuitionFeeEntity fee = unpaidFee();
        fee.setClassId(70L);
        when(tuitionFeeRepository.findAllById(any())).thenReturn(List.of(fee));
        when(studentRepository.findAllById(any())).thenReturn(List.of(
                StudentEntity.builder().id(8L).studentCode("SV01").fullName("Nguyen Van A").build()));
        when(classRepository.findAllById(any())).thenReturn(List.of(
                ClassEntity.builder().id(70L).classCode("L01").className("Lop 1").build()));
        when(paymentTransactionRepository.findByRefTransactionIdInAndIsDeleted(anyList(), eq(0)))
                .thenReturn(List.of(ref));

        PaymentTransactionFilterRequest filter = new PaymentTransactionFilterRequest();
        filter.setPageNo(2);
        filter.setPageSize(10);
        PageResponse<PaymentTransactionDto> page = service.search(filter);

        assertThat(page.getContent()).hasSize(2);
        PaymentTransactionDto refundRow = page.getContent().get(0);
        assertThat(refundRow.getTransactionType()).isEqualTo("REFUND");
        assertThat(refundRow.getRefundedAmount()).isNull();
        PaymentTransactionDto paymentRow = page.getContent().get(1);
        assertThat(paymentRow.getFeeCode()).isEqualTo("FEE01");
        assertThat(paymentRow.getStudentCode()).isEqualTo("SV01");
        assertThat(paymentRow.getClassName()).isEqualTo("Lop 1");
        assertThat(paymentRow.getRefundedAmount()).isEqualByComparingTo("250000");
        assertThat(paymentRow.getRefundableAmount()).isEqualByComparingTo("750000");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentTransactionRepository).findAll(ArgumentMatchers.<Specification<PaymentTransactionEntity>>any(),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("paymentDate").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        verify(studentRepository, never()).findById(any());
    }

    @Test
    void getDetail_paymentWithRefunds_listsRefundsAndFlags() {
        PaymentTransactionEntity pay = payment(50L, "1000000", "SUCCESS");
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(pay));
        when(tuitionFeeRepository.findAllById(any())).thenReturn(List.of(unpaidFee()));
        when(paymentTransactionRepository.findByRefTransactionIdInAndIsDeleted(anyList(), eq(0)))
                .thenReturn(List.of(refund(60L, 50L, "400000")));
        when(paymentTransactionRepository.findByRefTransactionIdAndIsDeletedOrderByPaymentDateDescIdDesc(50L, 0))
                .thenReturn(List.of(refund(60L, 50L, "400000")));
        when(tuitionFeeRepository.findById(4L)).thenReturn(Optional.of(unpaidFee()));

        PaymentTransactionDetailResponse detail = service.getDetail(50L);

        assertThat(detail.getTransaction().getId()).isEqualTo(50L);
        assertThat(detail.getRefunds()).hasSize(1);
        assertThat(detail.getFee().getFeeCode()).isEqualTo("FEE01");
        assertThat(detail.getFee().getRemainingAmount()).isEqualByComparingTo("1500000");
        assertThat(detail.isVoidable()).isFalse();
        assertThat(detail.isRefundable()).isTrue();
        assertThat(detail.getOriginalTransaction()).isNull();
    }

    @Test
    void getDetail_refundRow_includesOriginal() {
        PaymentTransactionEntity ref = refund(60L, 50L, "400000");
        when(paymentTransactionRepository.findByIdAndIsDeleted(60L, 0)).thenReturn(Optional.of(ref));
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0))
                .thenReturn(Optional.of(payment(50L, "1000000", "SUCCESS")));

        PaymentTransactionDetailResponse detail = service.getDetail(60L);

        assertThat(detail.getOriginalTransaction().getId()).isEqualTo(50L);
        assertThat(detail.isVoidable()).isFalse();
        assertThat(detail.isRefundable()).isFalse();
    }

    @Test
    void renderReceiptHtml_containsReceiptStudentAmountInWordsAndCashier() {
        PaymentTransactionEntity pay = payment(50L, "1250000", "SUCCESS");
        pay.setReceiptNo("PT20261000001");
        pay.setPayerName("Nguyen Van Bo");
        pay.setPaymentMethod("CASH");
        pay.setCreatedBy("accountant1");
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0)).thenReturn(Optional.of(pay));
        TuitionFeeEntity fee = unpaidFee();
        fee.setClassId(70L);
        fee.setFeeMonth(10);
        fee.setFeeYear(2026);
        when(tuitionFeeRepository.findById(4L)).thenReturn(Optional.of(fee));
        when(studentRepository.findById(8L)).thenReturn(Optional.of(
                StudentEntity.builder().id(8L).studentCode("SV01").fullName("Nguyen Van A").build()));
        when(classRepository.findById(70L)).thenReturn(Optional.of(
                ClassEntity.builder().id(70L).classCode("L01").className("Lop 1").build()));
        when(userRepository.findByUsernameAndIsDeleted("accountant1", 0)).thenReturn(Optional.of(
                UserEntity.builder().username("accountant1").fullName("Le Thu Ngan").build()));
        when(bankAccountService.requireActive()).thenReturn(activeAccount());

        String html = service.renderReceiptHtml(50L);

        assertThat(html).contains("PHIẾU THU HỌC PHÍ", "PT20261000001", "Nguyen Van A", "SV01", "Lop 1",
                "FEE01", "tháng 10/2026", "1.250.000đ", "Một triệu hai trăm năm mươi nghìn đồng", "Tiền mặt",
                "Le Thu Ngan", "Nguyen Van Bo", "TRUONG EDUCATION");
    }

    @Test
    void renderReceiptHtml_pendingTransaction_isRejected() {
        when(paymentTransactionRepository.findByIdAndIsDeleted(50L, 0))
                .thenReturn(Optional.of(payment(50L, "1", "PENDING")));

        assertThatThrownBy(() -> service.renderReceiptHtml(50L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("RECEIPT_NOT_AVAILABLE");
    }

    private void stubReceiptNo(String receiptNo) {
        when(jdbcTemplate.execute(eq(PaymentServiceImpl.NEXT_RECEIPT_NO_CALL),
                ArgumentMatchers.<CallableStatementCallback<String>>any())).thenReturn(receiptNo);
    }

    private void stubLockedFee(Long transactionId, TuitionFeeEntity fee) {
        when(paymentTransactionRepository.findTuitionFeeIdByIdAndIsDeleted(transactionId, 0))
                .thenReturn(Optional.of(fee.getId()));
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(fee.getId(), 0)).thenReturn(Optional.of(fee));
        lenient().when(tuitionFeeRepository.save(any(TuitionFeeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static PaymentTransactionEntity payment(Long id, String amount, String status) {
        return PaymentTransactionEntity.builder()
                .id(id)
                .transactionCode("PAY" + id)
                .tuitionFeeId(4L)
                .amount(new BigDecimal(amount))
                .paymentMethod("VIETQR")
                .paymentDate(LocalDateTime.of(2026, 10, 1, 9, 30))
                .status(status)
                .transactionType(DomainConstants.TRANSACTION_TYPE_PAYMENT)
                .isDeleted(0)
                .build();
    }

    private static PaymentTransactionEntity refund(Long id, Long refId, String amount) {
        return PaymentTransactionEntity.builder()
                .id(id)
                .transactionCode("RFD" + id)
                .tuitionFeeId(4L)
                .amount(new BigDecimal(amount))
                .paymentMethod("CASH")
                .paymentDate(LocalDateTime.of(2026, 10, 2, 9, 30))
                .status("SUCCESS")
                .transactionType(DomainConstants.TRANSACTION_TYPE_REFUND)
                .refTransactionId(refId)
                .isDeleted(0)
                .build();
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
