package com.education.base.service.impl;

import com.education.base.dto.request.GradeBatchRequest;
import com.education.base.dto.request.GradeUpsertRequest;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.mapper.FinanceAcademicMapperImpl;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.GradeRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeServiceImplTest {

    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ClassSessionRepository classSessionRepository;

    private GradeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GradeServiceImpl(gradeRepository, classRepository, classStudentRepository,
                studentRepository, attendanceRepository, new FinanceAcademicMapperImpl(),
                new TeachingAssignmentGuard(classSessionRepository));
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    @Test
    void upsertBatch_createsNewGradeWhenMissing() {
        when(classRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(2L).classCode("C01").className("TOEIC").isDeleted(0).build()));
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(StudentEntity.builder()
                .id(8L).studentCode("SV01").fullName("A").status("ACTIVE").isDeleted(0).build()));
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(2L, 8L, 0))
                .thenReturn(Optional.of(ClassStudentEntity.builder()
                        .classId(2L).studentId(8L).status("ENROLLED").isDeleted(0).build()));
        when(gradeRepository.findByClassIdAndStudentIdAndGradeType(2L, 8L, "MIDTERM"))
                .thenReturn(Optional.empty());
        when(gradeRepository.save(any(GradeEntity.class))).thenAnswer(invocation -> {
            GradeEntity entity = invocation.getArgument(0);
            entity.setId(21L);
            return entity;
        });

        GradeBatchRequest request = new GradeBatchRequest(List.of(GradeUpsertRequest.builder()
                .classId(2L).studentId(8L).gradeType("MIDTERM")
                .score(new BigDecimal("8.50")).weight(new BigDecimal("2")).build()));

        assertThat(service.upsertBatch(request)).hasSize(1);
        ArgumentCaptor<GradeEntity> captor = ArgumentCaptor.forClass(GradeEntity.class);
        verify(gradeRepository).save(captor.capture());
        assertThat(captor.getValue().getScore()).isEqualByComparingTo("8.50");
        assertThat(captor.getValue().getGradeType()).isEqualTo("MIDTERM");
    }

    @Test
    void upsertBatch_teacherOfOtherClass_isForbiddenBeforeWriting() {
        TestSecurityContexts.login(7L, List.of("ROLE_TEACHER"), Set.of("MENU_GRADE:CREATE", "MENU_GRADE:UPDATE"));
        when(classRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(2L).classCode("C01").className("TOEIC").teacherId(99L).isDeleted(0).build()));
        when(classSessionRepository.existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(2L, 7L, 0, "CANCELLED"))
                .thenReturn(false);

        GradeBatchRequest request = new GradeBatchRequest(List.of(GradeUpsertRequest.builder()
                .classId(2L).studentId(8L).gradeType("MIDTERM").score(new BigDecimal("8.50")).build()));

        assertThatThrownBy(() -> service.upsertBatch(request))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo("NOT_CLASS_TEACHER");
        verify(gradeRepository, never()).save(any());
    }
}
