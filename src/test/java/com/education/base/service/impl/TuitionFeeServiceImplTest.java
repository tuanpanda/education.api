package com.education.base.service.impl;

import com.education.base.config.PaymentProperties;
import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.mapper.FinanceAcademicMapperImpl;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private TuitionFeeServiceImpl service;

    @BeforeEach
    void setUp() {
        PaymentProperties paymentProperties = new PaymentProperties();
        paymentProperties.setBankBin("970436");
        paymentProperties.setAccountNo("1234567890");
        paymentProperties.setAccountName("TRUONG EDUCATION");
        service = new TuitionFeeServiceImpl(tuitionFeeRepository, paymentTransactionRepository,
                studentRepository, classRepository, fileStorageService, new FileMapperImpl(),
                new FinanceAcademicMapperImpl(), paymentProperties);
    }

    @Test
    void createQr_buildsEmvCoPayloadAndBase64Image() {
        when(tuitionFeeRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(studentRepository.findById(8L)).thenReturn(Optional.of(StudentEntity.builder()
                .id(8L).studentCode("SV01").fullName("Nguyen Van A").build()));

        TuitionQrResponseDto qr = service.createQr(4L, new TuitionQrRequest());

        assertThat(qr.getTuitionFeeId()).isEqualTo(4L);
        assertThat(qr.getRemainingAmount()).isEqualByComparingTo("1500000");
        assertThat(qr.getQrPayload()).startsWith("000201");
        assertThat(qr.getQrPayload()).contains("6304");
        assertThat(qr.getBase64Image()).startsWith("data:image/png;base64,");
        assertThat(qr.getQuickUrl()).contains("970436-1234567890");
    }

    @Test
    void confirmPayment_updatesPaidAmountAndStatusInSameSave() {
        when(tuitionFeeRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.of(unpaidFee()));
        when(paymentTransactionRepository.save(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
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
    }

    @Test
    void confirmPayment_alreadyPaid_throws() {
        TuitionFeeEntity fee = unpaidFee();
        fee.setPaidAmount(new BigDecimal("2000000"));
        fee.setStatus("PAID");
        when(tuitionFeeRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.of(fee));

        assertThatThrownBy(() -> service.confirmPayment(4L, new ConfirmPaymentRequest()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("FEE_ALREADY_PAID");
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
