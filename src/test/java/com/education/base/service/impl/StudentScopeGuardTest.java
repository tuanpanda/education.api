package com.education.base.service.impl;

import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.PortalStudentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentScopeGuardTest {

    @Mock
    private PortalStudentContext portalStudentContext;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private TuitionFeeRepository tuitionFeeRepository;
    @InjectMocks
    private StudentScopeGuard guard;

    @Test
    void enrolledClass_isReturned() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        ClassStudentEntity enrollment = ClassStudentEntity.builder().classId(3L).studentId(42L).status("ENROLLED")
                .isDeleted(0).build();
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(3L, 42L, 0))
                .thenReturn(Optional.of(enrollment));

        assertThat(guard.requireEnrolled(3L)).isSameAs(enrollment);
    }

    @Test
    void otherStudentsClass_isNotFound() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(9L, 42L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireEnrolled(9L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(StudentScopeGuard.CLASS_NOT_FOUND);
    }

    @Test
    void droppedEnrollment_isNotFound() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(3L, 42L, 0)).thenReturn(Optional.of(
                ClassStudentEntity.builder().classId(3L).studentId(42L).status("DROPPED").isDeleted(0).build()));

        assertThatThrownBy(() -> guard.requireEnrolled(3L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(StudentScopeGuard.CLASS_NOT_FOUND);
    }

    @Test
    void nullClassId_isNotFound() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);

        assertThatThrownBy(() -> guard.requireEnrolled(null)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(StudentScopeGuard.CLASS_NOT_FOUND);
    }

    @Test
    void ownFee_isReturned() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        TuitionFeeEntity fee = TuitionFeeEntity.builder().id(7L).studentId(42L).feeCode("HP1").isDeleted(0).build();
        when(tuitionFeeRepository.findByIdAndIsDeleted(7L, 0)).thenReturn(Optional.of(fee));

        assertThat(guard.requireOwnFee(7L)).isSameAs(fee);
    }

    @Test
    void otherStudentsFee_isNotFound() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(tuitionFeeRepository.findByIdAndIsDeleted(7L, 0)).thenReturn(Optional.of(
                TuitionFeeEntity.builder().id(7L).studentId(99L).feeCode("HP1").isDeleted(0).build()));

        assertThatThrownBy(() -> guard.requireOwnFee(7L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(StudentScopeGuard.FEE_NOT_FOUND);
    }

    @Test
    void missingFee_isNotFound() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(tuitionFeeRepository.findByIdAndIsDeleted(7L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireOwnFee(7L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(StudentScopeGuard.FEE_NOT_FOUND);
    }

    @Test
    void nullFeeId_isNotFound() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);

        assertThatThrownBy(() -> guard.requireOwnFee(null)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo(StudentScopeGuard.FEE_NOT_FOUND);
    }
}
