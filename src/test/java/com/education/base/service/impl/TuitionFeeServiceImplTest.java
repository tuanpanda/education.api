package com.education.base.service.impl;

import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.TuitionFeeReportDto;
import com.education.base.entity.ClassEntity;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
                StudentEntity.builder().id(9L).studentCode("SV02").fullName("Tran Thi B").build()));
        when(classRepository.findAllById(any())).thenReturn(List.of(
                ClassEntity.builder().id(70L).classCode("L01").className("Lop 1").build()));

        PageResponse<TuitionFeeReportDto> page = service.search(new TuitionFeeFilterRequest());

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent().get(0).getStudentCode()).isEqualTo("SV01");
        assertThat(page.getContent().get(0).getClassCode()).isNull();
        assertThat(page.getContent().get(1).getClassName()).isEqualTo("Lop 1");
        assertThat(page.getContent().get(2).getStudentName()).isEqualTo("Tran Thi B");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Long>> studentIds = ArgumentCaptor.forClass(Iterable.class);
        verify(studentRepository, times(1)).findAllById(studentIds.capture());
        assertThat(studentIds.getValue()).containsExactlyInAnyOrder(8L, 9L);
        verify(classRepository, times(1)).findAllById(any());
        verify(studentRepository, never()).findById(any());
        verify(classRepository, never()).findById(any());
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
