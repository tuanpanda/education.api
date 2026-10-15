package com.education.base.service.quiz.impl;

import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.QuizAnswerRepository;
import com.education.base.repository.QuizAttemptEventRepository;
import com.education.base.repository.QuizAttemptRepository;
import com.education.base.repository.QuizQuestionRepository;
import com.education.base.repository.QuizRepository;
import com.education.base.repository.QuizRuleRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.impl.StudentScopeGuard;
import com.education.base.testsupport.InMemoryTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BUG-GD4-1: HS mở đề lớp khác -> phải 404 QUIZ_NOT_FOUND (giống id không tồn tại), không được 500 do
 * UnexpectedRollbackException. Chạy qua proxy @Transactional thật (TransactionInterceptor + transaction manager
 * giữ đúng ngữ nghĩa rollback-only của Spring), không cần DB.
 */
class PortalQuizTransactionBoundaryTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalDateTime OPEN = LocalDateTime.of(2026, 10, 20, 8, 0);
    static final long ME = 42L;
    static final long MY_CLASS = 7L;
    static final long OTHER_CLASS = 8L;

    InMemoryTransactionManager tm = new InMemoryTransactionManager();
    QuizRepository quizRepository = mock(QuizRepository.class);
    QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    ClassStudentRepository classStudentRepository = mock(ClassStudentRepository.class);
    PortalStudentContext ctx = mock(PortalStudentContext.class);
    StudentScopeGuard guard;
    PortalQuizServiceImpl service;

    @SuppressWarnings("unchecked")
    static <T> T tx(T target, InMemoryTransactionManager tm) {
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        return (T) pf.getProxy();
    }

    static QuizEntity quiz(long id, long classId) {
        return QuizEntity.builder().id(id).classId(classId).title("KT").status("PUBLISHED").openAt(OPEN)
                .closeAt(OPEN.plusHours(1)).durationMin(30).maxAttempts(1).resultMode("IMMEDIATE").scorePolicy("HIGHEST")
                .shuffleQuestions(0).shuffleOptions(0).maxScore(BigDecimal.ONE).isDeleted(0).version(0L).build();
    }

    @BeforeEach
    void setUp() {
        when(ctx.requireCurrentStudentId()).thenReturn(ME);
        ClassStudentEntity enrolled = new ClassStudentEntity();
        enrolled.setStatus("ENROLLED");
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(eq(MY_CLASS), eq(ME), any()))
                .thenReturn(Optional.of(enrolled));
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(eq(OTHER_CLASS), eq(ME), any()))
                .thenReturn(Optional.empty());
        when(quizRepository.findByIdAndIsDeleted(eq(2L), any())).thenReturn(Optional.of(quiz(2L, OTHER_CLASS)));
        when(quizRepository.findByIdAndIsDeleted(eq(999L), any())).thenReturn(Optional.empty());
        guard = tx(new StudentScopeGuard(ctx, classStudentRepository, mock(TuitionFeeRepository.class)), tm);
        Clock clock = Clock.fixed(OPEN.plusMinutes(10).atZone(ZONE).toInstant(), ZONE);
        PortalQuizServiceImpl target = new PortalQuizServiceImpl(quizRepository, mock(QuizQuestionRepository.class),
                mock(QuizRuleRepository.class), attemptRepository, mock(QuizAnswerRepository.class),
                mock(QuizAttemptEventRepository.class), mock(ClassRepository.class), classStudentRepository, ctx, guard,
                mock(QuizGradingService.class), new QuizSaveRateLimiter(clock), new QuizJson(), clock, tm);
        service = tx(target, tm);
    }

    @Test
    void start_otherClassQuiz_is404_sameAsNonexistent_noUnexpectedRollback() {
        assertThatThrownBy(() -> service.start(2L, "ip", "ua")).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
        assertThatThrownBy(() -> service.start(999L, "ip", "ua")).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
        verify(attemptRepository, never()).saveAndFlush(any());
        assertThat(tm.depth()).isZero();
    }

    @Test
    void get_otherClassQuiz_is404_throughTransactionalProxy() {
        assertThatThrownBy(() -> service.get(2L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
    }

    @Test
    void attemptOfOtherClass_is404_throughTransactionalProxy() {
        when(attemptRepository.findById(anyLong())).thenReturn(Optional.of(com.education.base.entity.QuizAttemptEntity.builder()
                .id(50L).quizId(2L).studentId(ME).status("GRADED").build()));
        assertThatThrownBy(() -> service.result(50L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
    }

    /** Tái hiện nguyên nhân: bắt lỗi ném qua proxy @Transactional lồng nhau -> tx ngoài rollback-only -> 500. */
    @Test
    void rootCause_catchingExceptionFromNestedTransactionalCall_marksOuterRollbackOnly() {
        org.springframework.transaction.support.TransactionTemplate outer =
                new org.springframework.transaction.support.TransactionTemplate(tm);
        assertThatThrownBy(() -> outer.executeWithoutResult(st -> {
            try {
                guard.requireEnrolled(OTHER_CLASS);
            } catch (OracleBusinessException e) {
                // nuốt lỗi như code cũ
            }
        })).isInstanceOf(UnexpectedRollbackException.class);
        // Cách mới: isEnrolled không ném -> tx ngoài commit bình thường.
        outer.executeWithoutResult(st -> assertThat(guard.isEnrolled(OTHER_CLASS)).isFalse());
        assertThat(guard.isEnrolled(MY_CLASS)).isTrue();
    }
}
