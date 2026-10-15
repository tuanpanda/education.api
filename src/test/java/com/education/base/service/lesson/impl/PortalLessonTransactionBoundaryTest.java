package com.education.base.service.lesson.impl;

import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.impl.StudentScopeGuard;
import com.education.base.testsupport.InMemoryTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** QC-CODE-03: bài học lớp khác == id không tồn tại -> 404 LESSON_NOT_FOUND, qua proxy @Transactional thật. */
class PortalLessonTransactionBoundaryTest {

    static final long ME = 42L;
    static final long CLASS_A = 7L;
    static final long CLASS_B = 8L;

    LessonFakeDb db = new LessonFakeDb();
    InMemoryTransactionManager tm = new InMemoryTransactionManager();
    PortalStudentContext ctx = mock(PortalStudentContext.class);
    StudentScopeGuard guard;
    PortalLessonServiceImpl service;

    @SuppressWarnings("unchecked")
    static <T> T tx(T target, InMemoryTransactionManager tm) {
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        return (T) pf.getProxy();
    }

    @BeforeEach
    void setUp() {
        db.course(100L, "TOAN9", "ACTIVE");
        db.course(101L, "VAN9", "ACTIVE");
        db.clazz(CLASS_A, 5L, 100L);
        db.clazz(CLASS_B, 6L, 101L);
        db.enroll(1L, CLASS_A, ME);
        LessonSectionEntity sa = db.section(3L, null, CLASS_A, 0);
        LessonSectionEntity sB = db.section(4L, null, CLASS_B, 0);
        db.lesson(11L, sa, 0);
        db.lesson(15L, sB, 0);
        when(ctx.requireCurrentStudentId()).thenReturn(ME);
        when(db.classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(anyLong(), anyLong(), anyInt())).thenAnswer(i ->
                db.enrollments.values().stream().filter(e -> Objects.equals(e.getClassId(), i.getArgument(0))
                        && Objects.equals(e.getStudentId(), i.getArgument(1))).findFirst());
        guard = tx(new StudentScopeGuard(ctx, db.classStudentRepository, mock(TuitionFeeRepository.class)), tm);
        service = tx(new PortalLessonServiceImpl(db.lessonRepository, db.progressRepository, db.classStudentRepository,
                db.courseRepository, db.assignmentRepository, mock(FileStorageService.class), ctx, guard,
                mock(AuditService.class), db.access, db.catalog, db.clock), tm);
    }

    @Test
    void otherClassLesson_withClassId_is404_sameAsNonexistent() {
        assertThat(service.get(11L, CLASS_A).getId()).isEqualTo(11L);
        assertThatThrownBy(() -> service.get(15L, CLASS_B)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("LESSON_NOT_FOUND");
        assertThatThrownBy(() -> service.get(999L, CLASS_B)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("LESSON_NOT_FOUND");
        assertThatThrownBy(() -> service.get(999L, null)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("LESSON_NOT_FOUND");
        assertThatThrownBy(() -> service.recordProgress(15L, CLASS_B,
                com.education.base.dto.request.PortalLessonProgressRequest.builder().status("COMPLETED").build())).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("LESSON_NOT_FOUND");
        assertThat(tm.depth()).isZero();
    }

    @Test
    void oldPattern_swallowInOuterTx_unexpectedRollback_newPatternCommits() {
        TransactionTemplate outer = new TransactionTemplate(tm);
        assertThatThrownBy(() -> outer.executeWithoutResult(st -> {
            try {
                guard.requireEnrolled(CLASS_B);
            } catch (OracleBusinessException e) {
                // nuốt như code cũ
            }
        })).isInstanceOf(UnexpectedRollbackException.class);
        outer.executeWithoutResult(st -> assertThat(guard.isEnrolled(CLASS_B)).isFalse());
    }
}
