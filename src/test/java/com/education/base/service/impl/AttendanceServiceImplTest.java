package com.education.base.service.impl;

import com.education.base.dto.request.AttendanceMarkRequest;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
class AttendanceServiceImplTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassSessionRepository classSessionRepository;

    private AttendanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AttendanceServiceImpl(attendanceRepository, classRepository,
                classStudentRepository, studentRepository, new TeachingAssignmentGuard(classSessionRepository));
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    @Test
    void markBatch_upsertsInOneCallPerStudent() {
        when(classRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(3L).classCode("C01").className("TOEIC").isDeleted(0).build()));
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(3L, 9L, 0))
                .thenReturn(Optional.of(ClassStudentEntity.builder()
                        .classId(3L).studentId(9L).status("ENROLLED").isDeleted(0).build()));
        when(studentRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(StudentEntity.builder()
                .id(9L).studentCode("SV01").fullName("Nguyen Van A").status("ACTIVE").isDeleted(0).build()));
        when(attendanceRepository.findByClassIdAndStudentIdAndAttendanceDateAndIsDeleted(
                3L, 9L, LocalDate.of(2026, 9, 18), 0)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(AttendanceEntity.class))).thenAnswer(invocation -> {
            AttendanceEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });
        when(studentRepository.findById(9L)).thenReturn(Optional.of(StudentEntity.builder()
                .id(9L).studentCode("SV01").fullName("Nguyen Van A").build()));

        AttendanceMarkRequest request = AttendanceMarkRequest.builder()
                .classId(3L)
                .attendanceDate(LocalDate.of(2026, 9, 18))
                .entries(List.of(AttendanceMarkRequest.Entry.builder()
                        .studentId(9L).status("PRESENT").note("ok").build()))
                .build();

        assertThat(service.markBatch(request)).hasSize(1);
        ArgumentCaptor<AttendanceEntity> captor = ArgumentCaptor.forClass(AttendanceEntity.class);
        verify(attendanceRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PRESENT");
        assertThat(captor.getValue().getClassId()).isEqualTo(3L);
    }

    @Test
    void markBatch_studentNotEnrolled_throws() {
        when(classRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(3L).classCode("C01").className("TOEIC").isDeleted(0).build()));
        when(studentRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(StudentEntity.builder()
                .id(9L).studentCode("SV01").fullName("Nguyen Van A").status("ACTIVE").isDeleted(0).build()));
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(3L, 9L, 0))
                .thenReturn(Optional.empty());

        AttendanceMarkRequest request = AttendanceMarkRequest.builder()
                .classId(3L)
                .attendanceDate(LocalDate.of(2026, 9, 18))
                .entries(List.of(AttendanceMarkRequest.Entry.builder()
                        .studentId(9L).status("ABSENT").build()))
                .build();

        assertThatThrownBy(() -> service.markBatch(request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("STUDENT_NOT_ENROLLED");
    }

    @Test
    void markBatch_teacherOfOtherClass_isForbiddenBeforeWriting() {
        TestSecurityContexts.login(7L, List.of("ROLE_TEACHER"), Set.of("MENU_ATTENDANCE:CREATE"));
        when(classRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(3L).classCode("C01").className("TOEIC").teacherId(99L).isDeleted(0).build()));
        when(classSessionRepository.existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(3L, 7L, 0, "CANCELLED"))
                .thenReturn(false);

        AttendanceMarkRequest request = AttendanceMarkRequest.builder()
                .classId(3L)
                .attendanceDate(LocalDate.of(2026, 9, 18))
                .entries(List.of(AttendanceMarkRequest.Entry.builder().studentId(9L).status("PRESENT").build()))
                .build();

        assertThatThrownBy(() -> service.markBatch(request))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo("NOT_CLASS_TEACHER");
        verify(attendanceRepository, never()).save(any());
    }
}
