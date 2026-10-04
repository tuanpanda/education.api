package com.education.base.service.impl;

import com.education.base.dto.response.PortalMeResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.security.PortalStudentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalServiceImplTest {

    @Mock
    private PortalStudentContext portalStudentContext;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @InjectMocks
    private PortalServiceImpl service;

    @Test
    void me_returnsLinkedStudentProfileAndEnrolledClasses() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(studentRepository.findByIdAndIsDeleted(42L, 0)).thenReturn(Optional.of(StudentEntity.builder()
                .id(42L).studentCode("HS42").fullName("Nguyễn Văn A").dateOfBirth(LocalDate.of(2012, 5, 1))
                .parentName("Nguyễn Văn B").phone("0900000000").email("a@example.com").address("secret")
                .note("internal note").status("ACTIVE").isDeleted(0).build()));
        ClassEntity clazz = ClassEntity.builder().id(3L).classCode("L3").className("Lớp 3").status("ACTIVE").build();
        when(classStudentRepository.findActiveEnrollmentsWithClass(42L)).thenReturn(List.of(
                ClassStudentEntity.builder().clazz(clazz).build(),
                ClassStudentEntity.builder().clazz(null).build()));

        PortalMeResponse me = service.me();

        assertThat(me.getStudentId()).isEqualTo(42L);
        assertThat(me.getStudentCode()).isEqualTo("HS42");
        assertThat(me.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(me.getDateOfBirth()).isEqualTo(LocalDate.of(2012, 5, 1));
        assertThat(me.getGender()).isNull();
        assertThat(me.getParentName()).isEqualTo("Nguyễn Văn B");
        assertThat(me.getPhone()).isEqualTo("0900000000");
        assertThat(me.getEmail()).isEqualTo("a@example.com");
        assertThat(me.getClasses()).singleElement().satisfies(c -> {
            assertThat(c.getClassId()).isEqualTo(3L);
            assertThat(c.getClassCode()).isEqualTo("L3");
            assertThat(c.getClassName()).isEqualTo("Lớp 3");
            assertThat(c.getStatus()).isEqualTo("ACTIVE");
        });
        assertThat(me.toString()).doesNotContain("secret").doesNotContain("internal note");
    }

    @Test
    void me_studentRecordGone_isNotFound() {
        when(portalStudentContext.requireCurrentStudentId()).thenReturn(42L);
        when(studentRepository.findByIdAndIsDeleted(42L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me()).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("STUDENT_NOT_FOUND");
    }
}
