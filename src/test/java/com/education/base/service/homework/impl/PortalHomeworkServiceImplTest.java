package com.education.base.service.homework.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.dto.request.PortalSubmissionDraftRequest;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.entity.SubmissionHistoryEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.SubmissionHistoryRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.impl.StudentScopeGuard;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.education.base.service.homework.impl.HomeworkTestData.DUE;
import static com.education.base.service.homework.impl.HomeworkTestData.assignment;
import static com.education.base.service.homework.impl.HomeworkTestData.clockAt;
import static com.education.base.service.homework.impl.HomeworkTestData.file;
import static com.education.base.service.homework.impl.HomeworkTestData.submission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalHomeworkServiceImplTest {

    static final long ME = 42L;
    static final long OTHER = 43L;
    static final long MY_CLASS = 7L;
    static final long OTHER_CLASS = 8L;

    AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    SubmissionHistoryRepository historyRepository = mock(SubmissionHistoryRepository.class);
    ClassRepository classRepository = mock(ClassRepository.class);
    ClassSessionRepository sessionRepository = mock(ClassSessionRepository.class);
    ClassStudentRepository classStudentRepository = mock(ClassStudentRepository.class);
    PortalStudentContext ctx = mock(PortalStudentContext.class);
    StudentScopeGuard scopeGuard = mock(StudentScopeGuard.class);
    FileStorageService fileStorageService = mock(FileStorageService.class);
    FileLifecycleService fileLifecycleService = mock(FileLifecycleService.class);
    AuditService auditService = mock(AuditService.class);

    @BeforeEach
    void setUp() {
        when(ctx.requireCurrentStudentId()).thenReturn(ME);
        when(scopeGuard.requireEnrolled(OTHER_CLASS)).thenThrow(new OracleBusinessException("CLASS_NOT_FOUND", "x"));
        when(submissionRepository.saveAndFlush(any())).thenAnswer(i -> {
            SubmissionEntity s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(500L);
            }
            return s;
        });
        when(fileStorageService.getFilesByRef(anyString(), anyLong())).thenReturn(List.of());
    }

    PortalHomeworkServiceImpl at(LocalDateTime now) {
        return new PortalHomeworkServiceImpl(assignmentRepository, submissionRepository, historyRepository,
                classRepository, sessionRepository, classStudentRepository, ctx, scopeGuard, fileStorageService,
                fileLifecycleService, new HtmlContentSanitizer(), auditService, clockAt(now));
    }

    AssignmentEntity given(long id, long classId, String status, String type) {
        AssignmentEntity a = assignment(id, classId, status, type);
        when(assignmentRepository.findByIdAndIsDeleted(id, 0)).thenReturn(Optional.of(a));
        return a;
    }

    SubmissionEntity mine(long assignmentId, String status) {
        SubmissionEntity s = submission(500, assignmentId, ME, status);
        when(submissionRepository.findByAssignmentIdAndStudentIdAndIsDeleted(assignmentId, ME, 0))
                .thenReturn(Optional.of(s));
        return s;
    }

    static void assertCode(ThrowableAssert.ThrowingCallable c, String code) {
        assertThatThrownBy(c).isInstanceOf(OracleBusinessException.class)
                .extracting(e -> ((OracleBusinessException) e).getErrorCode()).isEqualTo(code);
    }

    // ---------------------------------------------------------------------------- hạn nộp (Clock.fixed)

    @Test
    void submit_onTime_notLate_createsHistory_andAudits() {
        given(1, MY_CLASS, "PUBLISHED", "TEXT");
        SubmissionEntity s = mine(1, "DRAFT");
        s.setContentText("<p>Bai lam</p>");
        var dto = at(DUE).submit(1L);
        assertThat(dto.getStatus()).isEqualTo("SUBMITTED");
        assertThat(dto.getLate()).isFalse();
        assertThat(s.getAttemptCount()).isEqualTo(1);
        assertThat(s.getFirstSubmittedAt()).isEqualTo(DUE);
        ArgumentCaptor<SubmissionHistoryEntity> h = ArgumentCaptor.forClass(SubmissionHistoryEntity.class);
        verify(historyRepository).save(h.capture());
        assertThat(h.getValue().getAttemptNo()).isEqualTo(1);
        assertThat(h.getValue().getContentSnapshot()).isEqualTo("<p>Bai lam</p>");
        verify(auditService).success(eq(AuditActions.HOMEWORK_SUBMITTED), eq(AuditActions.RESOURCE_SUBMISSION),
                eq(500L), anyMap());
    }

    @Test
    void submit_lateWithinLateUntil_isLate() {
        AssignmentEntity a = given(1, MY_CLASS, "PUBLISHED", "FILE");
        a.setAllowLate(1);
        a.setLateUntil(DUE.plusDays(1));
        mine(1, "DRAFT");
        when(fileStorageService.getFilesByRef("SUBMISSION", 500L)).thenReturn(List.of(file(9, "SUBMISSION", 500),
                file(10, "SUBMISSION", 500)));
        var dto = at(DUE.plusHours(3)).submit(1L);
        assertThat(dto.getLate()).isTrue();
        ArgumentCaptor<SubmissionHistoryEntity> h = ArgumentCaptor.forClass(SubmissionHistoryEntity.class);
        verify(historyRepository).save(h.capture());
        assertThat(h.getValue().getFileIds()).isEqualTo("9,10");
        assertThat(h.getValue().getIsLate()).isEqualTo(1);
    }

    @Test
    void submit_afterLateUntil_orNoLate_orClosed_isDeadlinePassed() {
        AssignmentEntity a = given(1, MY_CLASS, "PUBLISHED", "TEXT");
        SubmissionEntity s = mine(1, "DRAFT");
        s.setContentText("x");
        assertCode(() -> at(DUE.plusMinutes(1)).submit(1L), "SUBMISSION_DEADLINE_PASSED");
        a.setAllowLate(1);
        a.setLateUntil(DUE.plusDays(1));
        assertCode(() -> at(DUE.plusDays(1).plusMinutes(1)).submit(1L), "SUBMISSION_DEADLINE_PASSED");
        a.setStatus("CLOSED");
        assertCode(() -> at(DUE.minusDays(1)).submit(1L), "SUBMISSION_DEADLINE_PASSED");
    }

    @Test
    void returnedSubmission_canResubmitAfterDue_untilClosed() {
        AssignmentEntity a = given(1, MY_CLASS, "PUBLISHED", "TEXT");
        SubmissionEntity s = mine(1, "RETURNED");
        s.setContentText("sua lai");
        s.setAttemptCount(1);
        var dto = at(DUE.plusDays(5)).submit(1L);
        assertThat(dto.getStatus()).isEqualTo("SUBMITTED");
        assertThat(dto.getLate()).isTrue();
        assertThat(s.getAttemptCount()).isEqualTo(2);
        s.setStatus("RETURNED");
        a.setStatus("CLOSED");
        assertCode(() -> at(DUE.plusDays(5)).submit(1L), "SUBMISSION_DEADLINE_PASSED");
    }

    @Test
    void submit_empty_perSubmissionType() {
        given(1, MY_CLASS, "PUBLISHED", "TEXT");
        given(2, MY_CLASS, "PUBLISHED", "FILE");
        given(3, MY_CLASS, "PUBLISHED", "BOTH");
        mine(1, "DRAFT").setContentText("<p> </p>");
        mine(2, "DRAFT").setContentText(null);
        mine(3, "DRAFT");
        PortalHomeworkServiceImpl svc = at(DUE.minusHours(1));
        assertCode(() -> svc.submit(1L), "SUBMISSION_EMPTY");
        assertCode(() -> svc.submit(2L), "SUBMISSION_EMPTY");
        assertCode(() -> svc.submit(3L), "SUBMISSION_EMPTY");
        given(4, MY_CLASS, "PUBLISHED", "TEXT");
        assertCode(() -> svc.submit(4L), "SUBMISSION_EMPTY");
    }

    @Test
    void submitted_isLocked_forEditAndSubmit() {
        given(1, MY_CLASS, "PUBLISHED", "TEXT");
        mine(1, "SUBMITTED");
        PortalHomeworkServiceImpl svc = at(DUE.minusHours(1));
        assertCode(() -> svc.saveDraft(1L, new PortalSubmissionDraftRequest("x")), "SUBMISSION_LOCKED");
        assertCode(() -> svc.submit(1L), "SUBMISSION_LOCKED");
    }

    @Test
    void unsubmit_rules() {
        AssignmentEntity a = given(1, MY_CLASS, "PUBLISHED", "TEXT");
        SubmissionEntity s = mine(1, "SUBMITTED");
        assertThat(at(DUE.minusHours(1)).unsubmit(1L).getStatus()).isEqualTo("DRAFT");
        s.setStatus("SUBMITTED");
        assertCode(() -> at(DUE.plusHours(1)).unsubmit(1L), "SUBMISSION_DEADLINE_PASSED");
        a.setAllowResubmit(0);
        assertCode(() -> at(DUE.minusHours(1)).unsubmit(1L), "SUBMISSION_LOCKED");
        a.setAllowResubmit(1);
        s.setStatus("GRADED");
        assertCode(() -> at(DUE.minusHours(1)).unsubmit(1L), "SUBMISSION_LOCKED");
    }

    @Test
    void saveDraft_createsDraft_sanitizes_andTypeMismatchForFile() {
        given(1, MY_CLASS, "PUBLISHED", "TEXT");
        when(submissionRepository.findByAssignmentIdAndStudentIdAndIsDeleted(1L, ME, 0)).thenReturn(Optional.empty());
        var dto = at(DUE.minusHours(1)).saveDraft(1L, new PortalSubmissionDraftRequest("<p>a</p><script>x</script>"));
        assertThat(dto.getStatus()).isEqualTo("DRAFT");
        assertThat(dto.getContentText()).doesNotContain("script");
        given(2, MY_CLASS, "PUBLISHED", "FILE");
        assertCode(() -> at(DUE.minusHours(1)).saveDraft(2L, new PortalSubmissionDraftRequest("text")),
                "SUBMISSION_TYPE_MISMATCH");
    }

    @Test
    void uploadFile_textType_mismatch_andMaxFiles() {
        given(1, MY_CLASS, "PUBLISHED", "TEXT");
        assertCode(() -> at(DUE.minusHours(1)).uploadFile(1L, null), "SUBMISSION_TYPE_MISMATCH");
        AssignmentEntity a = given(2, MY_CLASS, "PUBLISHED", "FILE");
        a.setMaxFiles(1);
        mine(2, "DRAFT");
        when(fileStorageService.getFilesByRef("SUBMISSION", 500L)).thenReturn(List.of(file(9, "SUBMISSION", 500)));
        assertCode(() -> at(DUE.minusHours(1)).uploadFile(2L, null), "SUBMISSION_TOO_MANY_FILES");
        verify(fileStorageService, never()).storeFile(any(), anyString(), anyLong());
    }

    // ---------------------------------------------------------------------------- IDOR / phạm vi -> 404

    @Test
    void draftDeletedOrOtherClassAssignment_isNotFound() {
        given(1, MY_CLASS, "DRAFT", "TEXT");
        given(2, OTHER_CLASS, "PUBLISHED", "TEXT");
        PortalHomeworkServiceImpl svc = at(DUE.minusHours(1));
        assertCode(() -> svc.get(1L), "ASSIGNMENT_NOT_FOUND");
        assertCode(() -> svc.get(2L), "ASSIGNMENT_NOT_FOUND");
        assertCode(() -> svc.get(3L), "ASSIGNMENT_NOT_FOUND");
        assertCode(() -> svc.submit(2L), "ASSIGNMENT_NOT_FOUND");
    }

    @Test
    void studentOnlySeesOwnSubmission_neverOthers() {
        given(1, MY_CLASS, "PUBLISHED", "TEXT");
        SubmissionEntity others = submission(501, 1, OTHER, "GRADED");
        when(submissionRepository.findByAssignmentIdAndStudentIdAndIsDeleted(1L, OTHER, 0)).thenReturn(Optional.of(others));
        when(submissionRepository.findByAssignmentIdAndStudentIdAndIsDeleted(1L, ME, 0)).thenReturn(Optional.empty());
        assertThat(at(DUE.minusHours(1)).get(1L).getMySubmission()).isNull();
        verify(submissionRepository, never()).findByAssignmentIdAndStudentIdAndIsDeleted(1L, OTHER, 0);
    }

    @Test
    void deleteFile_ofOtherSubmission_isPortalFileNotFound() {
        given(1, MY_CLASS, "PUBLISHED", "FILE");
        mine(1, "DRAFT");
        when(fileStorageService.getFileEntity(77L)).thenReturn(file(77, "SUBMISSION", 501));
        assertCode(() -> at(DUE.minusHours(1)).deleteFile(1L, 77L), "PORTAL_FILE_NOT_FOUND");
        when(fileStorageService.getFileEntity(78L)).thenThrow(new OracleBusinessException("FILE_NOT_FOUND", "x"));
        assertCode(() -> at(DUE.minusHours(1)).deleteFile(1L, 78L), "PORTAL_FILE_NOT_FOUND");
        verify(fileLifecycleService, never()).softDelete(anyLong(), anyString());
    }

    @Test
    void detail_hidesScoreUnlessGraded() {
        given(1, MY_CLASS, "PUBLISHED", "TEXT");
        SubmissionEntity s = mine(1, "RETURNED");
        s.setScore(java.math.BigDecimal.TEN);
        s.setFeedback("lam lai");
        s.setReturnReason("thieu");
        var d = at(DUE.minusHours(1)).get(1L);
        assertThat(d.getMySubmission().getScore()).isNull();
        assertThat(d.getMySubmission().getFeedback()).isEqualTo("lam lai");
        assertThat(d.getCanSubmit()).isTrue();
        assertThat(d.getServerNow()).isEqualTo(DUE.minusHours(1));
    }

    // ---------------------------------------------------------------------------- tab + summary

    @Test
    void list_tabs_andSummary() {
        ClassStudentEntity cs = new ClassStudentEntity();
        cs.setClassId(MY_CLASS);
        ClassEntity c = new ClassEntity();
        c.setId(MY_CLASS);
        c.setClassName("Lop 7");
        cs.setClazz(c);
        when(classStudentRepository.findActiveEnrollmentsWithClass(ME)).thenReturn(List.of(cs));
        AssignmentEntity todo1 = assignment(1, MY_CLASS, "PUBLISHED", "TEXT");
        AssignmentEntity todo2 = assignment(2, MY_CLASS, "PUBLISHED", "TEXT");
        todo2.setDueAt(DUE.plusDays(10));
        AssignmentEntity overdue = assignment(3, MY_CLASS, "CLOSED", "TEXT");
        AssignmentEntity graded = assignment(4, MY_CLASS, "PUBLISHED", "TEXT");
        when(assignmentRepository.findByClassIdInAndIsDeletedAndStatusIn(any(), eq(0), any()))
                .thenReturn(List.of(todo2, overdue, todo1, graded));
        SubmissionEntity g = submission(504, 4, ME, "GRADED");
        g.setScore(java.math.BigDecimal.ONE);
        when(submissionRepository.findByStudentIdAndAssignmentIdInAndIsDeleted(eq(ME), any(), eq(0)))
                .thenReturn(List.of(g));
        PortalHomeworkServiceImpl svc = at(DUE.minusDays(1));
        assertThat(svc.list(null)).extracting(i -> i.getId()).containsExactly(1L, 2L);
        assertThat(svc.list("overdue")).extracting(i -> i.getId()).containsExactly(3L);
        assertThat(svc.list("graded").get(0).getScore()).isEqualByComparingTo("1");
        assertThat(svc.list("submitted")).isEmpty();
        assertCode(() -> svc.list("xyz"), "VALIDATION_ERROR");
        var sum = svc.summary();
        assertThat(sum.getTodoCount()).isEqualTo(2);
        assertThat(sum.getDueSoonCount()).isEqualTo(1);
        assertThat(sum.getOverdueCount()).isEqualTo(1);
        assertThat(sum.getNextDueAt()).isEqualTo(DUE);
    }
}
