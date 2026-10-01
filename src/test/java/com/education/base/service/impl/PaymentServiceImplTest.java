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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(tuitionFeeRepository, paymentTransactionRepository,
                bankAccountService, new PaymentTransactionMapperImpl());
    }

    // ---- confirmPayment: chuyển nguyên trạng từ TuitionFeeServiceImplTest (B-1) ----------------

    @Test
    void confirmPayment_updatesPaidAmountAndStatusInSameSave() {
        when(tuitionFeeRepository.findByIdAndIsDeletedForUpdate(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(bankAccountService.requireActive()).thenReturn(activeAccount());
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
