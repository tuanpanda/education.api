package com.education.base.service.impl;

import com.education.base.entity.ClassEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeachingAssignmentGuardTest {

    private static final long TEACHER_ID = 7L;

    @Mock
    private ClassSessionRepository classSessionRepository;

    private TeachingAssignmentGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TeachingAssignmentGuard(classSessionRepository);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    private static ClassEntity classTaughtBy(Long teacherId) {
        return ClassEntity.builder().id(3L).classCode("C01").teacherId(teacherId).isDeleted(0).build();
    }

    private static void loginTeacher(String... extraRoles) {
        List<String> roles = new java.util.ArrayList<>(List.of("ROLE_TEACHER"));
        roles.addAll(List.of(extraRoles));
        TestSecurityContexts.login(TEACHER_ID, roles, Set.of("MENU_GRADE:UPDATE", "MENU_ATTENDANCE:UPDATE"));
    }

    @Test
    void classTeacher_isAllowedWithoutSessionLookup() {
        loginTeacher();

        assertThatCode(() -> guard.requireCanWrite(classTaughtBy(TEACHER_ID), "x")).doesNotThrowAnyException();
        verify(classSessionRepository, never()).existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(
                any(), any(), any(), any());
    }

    @Test
    void sessionTeacher_isAllowed() {
        loginTeacher();
        when(classSessionRepository.existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(3L, TEACHER_ID, 0, "CANCELLED"))
                .thenReturn(true);

        assertThatCode(() -> guard.requireCanWrite(classTaughtBy(99L), "x")).doesNotThrowAnyException();
    }

    @Test
    void teacherOfOtherClass_isForbidden() {
        loginTeacher();
        when(classSessionRepository.existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(3L, TEACHER_ID, 0, "CANCELLED"))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.requireCanWrite(classTaughtBy(99L), "Bạn chỉ được nhập điểm cho lớp mình."))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn chỉ được nhập điểm cho lớp mình.")
                .extracting("errorCode").isEqualTo("NOT_CLASS_TEACHER");
    }

    @Test
    void classWithoutTeacher_isForbiddenForTeacherWithoutSessions() {
        loginTeacher();

        assertThatThrownBy(() -> guard.requireCanWrite(classTaughtBy(null), "x"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void teacherWhoIsAlsoAdmin_isNotRestricted() {
        loginTeacher("ROLE_ADMIN");

        assertThatCode(() -> guard.requireCanWrite(classTaughtBy(99L), "x")).doesNotThrowAnyException();
        verify(classSessionRepository, never()).existsByClassIdAndTeacherIdAndIsDeletedAndStatusNot(
                any(), any(), any(), any());
    }

    @Test
    void nonTeacherStaff_isNotRestricted() {
        TestSecurityContexts.login(8L, List.of("ROLE_ACADEMIC"), Set.of("MENU_GRADE:UPDATE"));

        assertThatCode(() -> guard.requireCanWrite(classTaughtBy(99L), "x")).doesNotThrowAnyException();
    }

    @Test
    void noAuthenticatedUser_isNotRestricted() {
        assertThatCode(() -> guard.requireCanWrite(classTaughtBy(99L), "x")).doesNotThrowAnyException();
    }
}
