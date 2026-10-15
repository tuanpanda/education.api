package com.education.base.service.homework.impl;

import com.education.base.common.HtmlContentSanitizer;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.SubmissionHistoryRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.impl.StudentScopeGuard;
import com.education.base.service.lesson.impl.LessonFileAccess;
import com.education.base.testsupport.InMemoryTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static com.education.base.service.homework.impl.HomeworkTestData.DUE;
import static com.education.base.service.homework.impl.HomeworkTestData.assignment;
import static com.education.base.service.homework.impl.HomeworkTestData.clockAt;
import static com.education.base.service.homework.impl.HomeworkTestData.file;
import static com.education.base.service.homework.impl.HomeworkTestData.submission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phòng ngừa (cùng nguyên nhân BUG-GD4-1): kiểm tra ghi danh trong cổng Bài tập / file cổng không được bắt lỗi ném
 * qua proxy {@code @Transactional} của {@link StudentScopeGuard}. Chạy qua proxy giao dịch thật, không DB.
 */
class PortalHomeworkTransactionBoundaryTest {

    static final long ME = 42L;
    static final long MY_CLASS = 7L;
    static final long OTHER_CLASS = 8L;

    InMemoryTransactionManager tm = new InMemoryTransactionManager();
    PortalStudentContext ctx = mock(PortalStudentContext.class);
    ClassStudentRepository classStudentRepository = mock(ClassStudentRepository.class);
    AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    FileStorageService fileStorageService = mock(FileStorageService.class);
    StudentScopeGuard guard;
    PortalHomeworkServiceImpl homework;
    PortalFileServiceImpl files;

    @SuppressWarnings("unchecked")
    static <T> T tx(T target, InMemoryTransactionManager tm) {
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        return (T) pf.getProxy();
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
        when(assignmentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(assignment(3, OTHER_CLASS, "PUBLISHED", "BOTH")));
        when(assignmentRepository.findByIdAndIsDeleted(999L, 0)).thenReturn(Optional.empty());
        // Bài nộp của chính HS ở bài lớp khác (HS đã rút) + file nhận xét GV (FEEDBACK) đính kèm bài đã chấm.
        when(submissionRepository.findById(30L)).thenReturn(Optional.of(submission(30, 3, ME, "GRADED")));
        when(fileStorageService.getFileEntity(500L)).thenReturn(file(500, "FEEDBACK", 30));
        when(fileStorageService.getFileEntity(501L)).thenReturn(file(501, "SUBMISSION", 30));
        when(fileStorageService.getFileEntity(502L)).thenReturn(file(502, "ASSIGNMENT", 3));

        guard = tx(new StudentScopeGuard(ctx, classStudentRepository, mock(TuitionFeeRepository.class)), tm);
        homework = tx(new PortalHomeworkServiceImpl(assignmentRepository, submissionRepository,
                mock(SubmissionHistoryRepository.class), mock(ClassRepository.class), mock(ClassSessionRepository.class),
                classStudentRepository, ctx, guard, fileStorageService, mock(FileLifecycleService.class),
                new HtmlContentSanitizer(), mock(AuditService.class), clockAt(DUE.minusDays(1))), tm);
        files = tx(new PortalFileServiceImpl(fileStorageService, assignmentRepository, submissionRepository, ctx, guard,
                mock(AuditService.class), mock(LessonFileAccess.class)), tm);
    }

    @Test
    void homework_otherClassAssignment_is404_sameAsNonexistent_throughTransactionalProxy() {
        assertThatThrownBy(() -> homework.get(3L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ASSIGNMENT_NOT_FOUND");
        assertThatThrownBy(() -> homework.get(999L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ASSIGNMENT_NOT_FOUND");
        assertThatThrownBy(() -> homework.submit(3L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ASSIGNMENT_NOT_FOUND");
        assertThat(tm.depth()).isZero();
    }

    @Test
    void portalFile_feedbackSubmissionAndAssignmentFiles_ofOtherClass_are404_throughTransactionalProxy() {
        for (long id : new long[]{500L, 501L, 502L}) {
            assertThatThrownBy(() -> files.serve(id, false)).isInstanceOf(OracleBusinessException.class)
                    .extracting("errorCode").isEqualTo("PORTAL_FILE_NOT_FOUND");
        }
        assertThat(tm.depth()).isZero();
    }

    /** Mẫu cũ: bắt lỗi requireEnrolled rồi tiếp tục trong tx ngoài -> commit ném UnexpectedRollbackException (500). */
    @Test
    void oldPattern_swallowInsideOuterTransaction_throwsUnexpectedRollback_newPatternCommits() {
        TransactionTemplate outer = new TransactionTemplate(tm);
        assertThatThrownBy(() -> outer.executeWithoutResult(st -> {
            boolean visible;
            try {
                guard.requireEnrolled(OTHER_CLASS);
                visible = true;
            } catch (OracleBusinessException e) {
                visible = false;
            }
            assertThat(visible).isFalse();
        })).isInstanceOf(UnexpectedRollbackException.class);

        int commits = tm.commits;
        outer.executeWithoutResult(st -> assertThat(guard.isEnrolled(OTHER_CLASS)).isFalse());
        assertThat(tm.commits).isEqualTo(commits + 1);
        // và qua service mới: 404 nghiệp vụ, không phải UnexpectedRollbackException
        assertThatThrownBy(() -> outer.executeWithoutResult(st -> homework.get(3L)))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("ASSIGNMENT_NOT_FOUND");
    }
}
