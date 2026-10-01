package com.education.base.service.impl;

import com.education.base.dto.request.StudentDiscountUpsertRequest;
import com.education.base.dto.response.StudentDiscountDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.StudentDiscountEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.StudentDiscountMapper;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.StudentDiscountRepository;
import com.education.base.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDiscountServiceImplTest {

    @Mock
    private StudentDiscountRepository studentDiscountRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassRepository classRepository;

    private StudentDiscountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentDiscountServiceImpl(studentDiscountRepository, studentRepository, classRepository,
                Mappers.getMapper(StudentDiscountMapper.class));
    }

    @Test
    void computeDiscount_sumsPercentAndAmountAndCapsAtTotal() {
        List<StudentDiscountEntity> discounts = List.of(
                discount("PERCENT", "15"),
                discount("AMOUNT", "50000"));
        assertThat(service.computeDiscount(new BigDecimal("200000"), discounts)).isEqualByComparingTo("80000");
        assertThat(service.computeDiscount(new BigDecimal("40000"), discounts)).isEqualByComparingTo("40000");
        assertThat(service.computeDiscount(new BigDecimal("200000"), List.of())).isEqualByComparingTo("0");
        assertThat(service.computeDiscount(BigDecimal.ZERO, discounts)).isEqualByComparingTo("0");
        assertThat(service.computeDiscount(null, discounts)).isEqualByComparingTo("0");
    }

    @Test
    void computeDiscount_percentIsRoundedToWholeDong() {
        assertThat(service.computeDiscount(new BigDecimal("80001"), List.of(discount("PERCENT", "33.33"))))
                .isEqualByComparingTo("26664");
        assertThat(service.computeDiscount(new BigDecimal("500000"), List.of(discount("PERCENT", "100"))))
                .isEqualByComparingTo("500000");
    }

    @Test
    void create_validatesAndSavesWithStudentAndClassNames() {
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(student()));
        when(classRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(clazz()));
        when(studentRepository.findAllById(List.of(8L))).thenReturn(List.of(student()));
        when(classRepository.findAllById(List.of(3L))).thenReturn(List.of(clazz()));
        when(studentDiscountRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StudentDiscountEntity entity = invocation.getArgument(0);
            entity.setId(5L);
            return entity;
        });

        StudentDiscountDto dto = service.create(request("PERCENT", "50")
                .classId(3L).reason("  Học bổng HK1  ").build());

        ArgumentCaptor<StudentDiscountEntity> captor = ArgumentCaptor.forClass(StudentDiscountEntity.class);
        verify(studentDiscountRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getIsDeleted()).isZero();
        assertThat(captor.getValue().getReason()).isEqualTo("Học bổng HK1");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getStudentCode()).isEqualTo("SV01");
        assertThat(dto.getClassName()).isEqualTo("Lớp A");
        assertThat(dto.getDiscountValue()).isEqualByComparingTo("50");
    }

    @Test
    void create_percentAbove100_isRejected() {
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(student()));

        assertThatThrownBy(() -> service.create(request("PERCENT", "120").build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("INVALID_DISCOUNT_VALUE");
        verify(studentDiscountRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_validToBeforeValidFrom_isRejected() {
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(student()));

        assertThatThrownBy(() -> service.create(request("AMOUNT", "100000")
                .validFrom(LocalDate.of(2026, 5, 1)).validTo(LocalDate.of(2026, 4, 30)).build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("INVALID_DISCOUNT_PERIOD");
    }

    @Test
    void create_unknownStudent_isRejected() {
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("AMOUNT", "100000").build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("STUDENT_NOT_FOUND");
    }

    @Test
    void update_missingDiscount_isNotFound() {
        when(studentDiscountRepository.findByIdAndIsDeleted(77L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(77L, request("AMOUNT", "1").build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("DISCOUNT_NOT_FOUND");
    }

    @Test
    void softDelete_setsDeletedFlag() {
        StudentDiscountEntity entity = discount("AMOUNT", "1000");
        entity.setId(5L);
        entity.setIsDeleted(0);
        when(studentDiscountRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(entity));

        service.softDelete(5L);

        assertThat(entity.getIsDeleted()).isEqualTo(1);
        verify(studentDiscountRepository).save(entity);
    }

    @Test
    void findApplicable_groupsByStudent() {
        StudentDiscountEntity a = discount("AMOUNT", "1000");
        a.setStudentId(8L);
        StudentDiscountEntity b = discount("PERCENT", "5");
        b.setStudentId(9L);
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 30);
        when(studentDiscountRepository.findApplicable(List.of(8L, 9L), 3L, from, to)).thenReturn(List.of(a, b));

        Map<Long, List<StudentDiscountEntity>> result = service.findApplicable(List.of(8L, 9L), 3L, from, to);

        assertThat(result.get(8L)).containsExactly(a);
        assertThat(result.get(9L)).containsExactly(b);
        assertThat(service.findApplicable(List.of(), 3L, from, to)).isEmpty();
    }

    private static StudentDiscountEntity discount(String type, String value) {
        return StudentDiscountEntity.builder().studentId(8L).discountType(type)
                .discountValue(new BigDecimal(value)).validFrom(LocalDate.of(2026, 1, 1)).build();
    }

    private static StudentDiscountUpsertRequest.StudentDiscountUpsertRequestBuilder request(String type, String value) {
        return StudentDiscountUpsertRequest.builder()
                .studentId(8L)
                .discountType(type)
                .discountValue(new BigDecimal(value))
                .validFrom(LocalDate.of(2026, 1, 1));
    }

    private static StudentEntity student() {
        return StudentEntity.builder().id(8L).studentCode("SV01").fullName("Nguyễn Văn A")
                .status("ACTIVE").isDeleted(0).build();
    }

    private static ClassEntity clazz() {
        return ClassEntity.builder().id(3L).classCode("LH01").className("Lớp A").build();
    }
}
