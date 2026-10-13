package com.education.base.service.homework.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.dto.request.AssignmentUpsertRequest;
import com.education.base.dto.request.GradeSubmissionRequest;
import com.education.base.dto.request.ReturnSubmissionRequest;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.SubmissionDetailDto;
import com.education.base.dto.response.SubmissionMatrixDto;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.entity.SubmissionHistoryEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.SubmissionHistoryRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.repository.UserRepository;
import com.education.base.security.Permissions;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.file.FileLimitService;
import com.education.base.service.homework.HomeworkGradebookSync;
import com.education.base.service.impl.TeachingAssignmentGuard;
import com.education.base.support.TestSecurityContexts;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeworkServiceImplTest {

    static final long CLASS_ID = 7L;

    AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    SubmissionHistoryRepository historyRepository = mock(SubmissionHistoryRepository.class);
    ClassRepository classRepository = mock(ClassRepository.class);
    ClassSessionRepository sessionRepository = mock(ClassSessionRepository.class);
    ClassStudentRepository classStudentRepository = mock(ClassStudentRepository.class);
    StudentRepository studentRepository = mock(StudentRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    FileStorageService fileStorageService = mock(FileStorageService.class);
    FileLifecycleService fileLifecycleService = mock(FileLifecycleService.class);
    FileLimitService fileLimitService = mock(FileLimitService.class);
    FileMapper fileMapper = mock(FileMapper.class);
    TeachingAssignmentGuard guard = mock(TeachingAssignmentGuard.class);
    AuditService auditService = mock(AuditService.class);
    HomeworkGradebookSync sync = mock(HomeworkGradebookSync.class);
    HomeworkServiceImpl service;

    @BeforeEach
    void setUp() {
        TestSecurityContexts.login(1L, List.of("ROLE_TEACHER"), Set.of(Permissions.HOMEWORK_VIEW,
                Permissions.HOMEWORK_UPDATE, Permissions.HOMEWORK_GRADE, Permissions.HOMEWORK_CREATE));
        service = newService(DUE.minusDays(1));
        ClassEntity clazz = new ClassEntity();
        clazz.setId(CLASS_ID);
        clazz.setClassCode("L7");
        clazz.setClassName("Lop 7");
        clazz.setIsDeleted(0);
        when(classRepository.findByIdAndIsDeleted(CLASS_ID, 0)).thenReturn(Optional.of(clazz));
        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.of(clazz));
        when(guard.taughtClassIdsIfRestricted()).thenReturn(Optional.empty());
        when(fileLimitService.limits("SUBMISSION")).thenReturn(new FileLimitService.ModuleLimits("SUBMISSION",
                10L << 20, 5, null, List.of("pdf"), 30, true, true));
        when(fileMapper.toDto(any())).thenAnswer(i -> {
            FileResponseDto d = new FileResponseDto();
            d.setId(((FileEntity) i.getArgument(0)).getId());
            return d;
        });
        when(assignmentRepository.saveAndFlush(any())).thenAnswer(i -> {
            AssignmentEntity a = i.getArgument(0);
            if (a.getId() == null) {
                a.setId(100L);
            }
            return a;
        });
        when(submissionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(fileStorageService.getFilesByRef(anyString(), anyLong())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    HomeworkServiceImpl newService(LocalDateTime now) {
        return new HomeworkServiceImpl(assignmentRepository, submissionRepository, historyRepository, classRepository,
                sessionRepository, classStudentRepository, studentRepository, userRepository, fileStorageService,
                fileLifecycleService, fileLimitService, fileMapper, guard, new HtmlContentSanitizer(), auditService,
                sync, clockAt(now));
    }

    static AssignmentUpsertRequest request(String type) {
        return AssignmentUpsertRequest.builder().classId(CLASS_ID).title(" Bai 1 ").description("<p>x</p><script>a</script>")
                .submissionType(type).maxScore(BigDecimal.TEN).dueAt(DUE).maxFiles(3).build();
    }

    static void assertCode(ThrowableAssert.ThrowingCallable c, String code) {
        assertThatThrownBy(c).isInstanceOf(OracleBusinessException.class)
                .extracting(e -> ((OracleBusinessException) e).getErrorCode()).isEqualTo(code);
    }

    // ------------------------------------------------------------------------------- tạo / sửa

    @Test
    void create_draft_sanitizesAndAudits() {
        var dto = service.create(request("BOTH"));
        assertThat(dto.getStatus()).isEqualTo("DRAFT");
        assertThat(dto.getTitle()).isEqualTo("Bai 1");
        assertThat(dto.getDescription()).doesNotContain("script");
        assertThat(dto.getCanManage()).isTrue();
        verify(auditService).success(eq(AuditActions.HOMEWORK_ASSIGNMENT_CREATED), eq(AuditActions.RESOURCE_ASSIGNMENT),
                eq(100L), anyMap());
    }

    @Test
    void create_textType_forcesMaxFilesZero() {
        assertThat(service.create(request("TEXT")).getMaxFiles()).isZero();
    }

    @Test
    void create_lateUntilRules() {
        AssignmentUpsertRequest r = request("FILE");
        r.setLateUntil(DUE.plusDays(1));
        assertCode(() -> service.create(r), "ASSIGNMENT_INVALID");
        r.setAllowLate(true);
        r.setLateUntil(DUE);
        assertCode(() -> service.create(r), "ASSIGNMENT_INVALID");
        r.setLateUntil(DUE.plusDays(1));
        assertThat(service.create(r).getLateUntil()).isEqualTo(DUE.plusDays(1));
    }

    @Test
    void create_maxFilesAboveModuleLimit_rejected() {
        AssignmentUpsertRequest r = request("FILE");
        r.setMaxFiles(6);
        assertCode(() -> service.create(r), "ASSIGNMENT_INVALID");
    }

    @Test
    void create_sessionOfOtherClassOrCancelled_rejected() {
        ClassSessionEntity other = new ClassSessionEntity();
        other.setId(5L);
        other.setClassId(99L);
        other.setStatus("SCHEDULED");
        when(sessionRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(other));
        AssignmentUpsertRequest r = request("TEXT");
        r.setSessionId(5L);
        assertCode(() -> service.create(r), "ASSIGNMENT_SESSION_INVALID");
        other.setClassId(CLASS_ID);
        other.setStatus("CANCELLED");
        assertCode(() -> service.create(r), "ASSIGNMENT_SESSION_INVALID");
        other.setStatus("SCHEDULED");
        assertThat(service.create(r).getSessionId()).isEqualTo(5L);
    }

    @Test
    void teacherOutsideClass_gets403() {
        doThrow(new ForbiddenException("NOT_CLASS_TEACHER", "x")).when(guard).requireCanWrite(any(), anyString());
        assertThatThrownBy(() -> service.create(request("TEXT"))).isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getErrorCode()).isEqualTo("NOT_CLASS_TEACHER");
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(
                assignment(1, CLASS_ID, "PUBLISHED", "TEXT")));
        assertThatThrownBy(() -> service.getSubmissionMatrix(1L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void search_teacherWithoutClasses_returnsEmpty() {
        when(guard.taughtClassIdsIfRestricted()).thenReturn(Optional.of(Set.of()));
        assertThat(service.search(null).getContent()).isEmpty();
        verify(assignmentRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void update_afterPublish_cannotChangeClass_orTypeWithSubmissions() {
        AssignmentEntity a = assignment(1, CLASS_ID, "PUBLISHED", "BOTH");
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(a));
        AssignmentUpsertRequest r = request("BOTH");
        r.setClassId(8L);
        assertCode(() -> service.update(1L, r), "ASSIGNMENT_INVALID_STATE");
        when(submissionRepository.existsByAssignmentIdAndIsDeleted(1L, 0)).thenReturn(true);
        assertCode(() -> service.update(1L, request("TEXT")), "ASSIGNMENT_INVALID_STATE");
    }

    @Test
    void update_maxScoreChange_resyncsGradebook() {
        AssignmentEntity a = assignment(1, CLASS_ID, "PUBLISHED", "BOTH");
        a.setSyncToGradebook(1);
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(a));
        AssignmentUpsertRequest r = request("BOTH");
        r.setSyncToGradebook(true);
        r.setMaxScore(BigDecimal.valueOf(20));
        service.update(1L, r);
        verify(sync).resyncAssignment(a);
    }

    @Test
    void publishCloseReopen_transitions() {
        AssignmentEntity a = assignment(1, CLASS_ID, "DRAFT", "TEXT");
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(a));
        assertCode(() -> service.close(1L), "ASSIGNMENT_INVALID_STATE");
        assertThat(service.publish(1L).getStatus()).isEqualTo("PUBLISHED");
        LocalDateTime firstPublished = a.getPublishedAt();
        assertThat(firstPublished).isEqualTo(DUE.minusDays(1));
        assertCode(() -> service.publish(1L), "ASSIGNMENT_INVALID_STATE");
        assertThat(service.close(1L).getStatus()).isEqualTo("CLOSED");
        assertThat(service.publish(1L).getStatus()).isEqualTo("PUBLISHED");
        assertThat(a.getPublishedAt()).isEqualTo(firstPublished);
        verify(auditService).success(eq(AuditActions.HOMEWORK_ASSIGNMENT_REOPENED), anyString(), eq(1L), anyMap());
    }

    @Test
    void delete_withSubmittedWork_rejected_draftOnlyOk() {
        AssignmentEntity a = assignment(1, CLASS_ID, "PUBLISHED", "TEXT");
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(a));
        when(submissionRepository.findByAssignmentIdAndIsDeleted(1L, 0)).thenReturn(List.of(
                submission(10, 1, 50, "SUBMITTED")));
        assertCode(() -> service.softDelete(1L), "ASSIGNMENT_HAS_SUBMISSIONS");
        when(submissionRepository.findByAssignmentIdAndIsDeleted(1L, 0)).thenReturn(List.of(
                submission(10, 1, 50, "DRAFT")));
        when(fileStorageService.getFilesByRef("ASSIGNMENT", 1L)).thenReturn(List.of(file(900, "ASSIGNMENT", 1)));
        service.softDelete(1L);
        assertThat(a.getIsDeleted()).isEqualTo(1);
        verify(fileLifecycleService).softDelete(eq(900L), anyString());
    }

    @Test
    void deleteAttachment_fileOfOtherAssignment_isFileNotFound() {
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(
                assignment(1, CLASS_ID, "DRAFT", "TEXT")));
        when(fileStorageService.getFileEntity(900L)).thenReturn(file(900, "ASSIGNMENT", 2));
        assertCode(() -> service.deleteAttachment(1L, 900L), "FILE_NOT_FOUND");
        verify(fileLifecycleService, never()).softDelete(anyLong(), anyString());
    }

    // ------------------------------------------------------------------------------- ma trận

    @Test
    void matrix_includesNotSubmittedAndMissing_sortedByName() {
        AssignmentEntity a = assignment(1, CLASS_ID, "PUBLISHED", "TEXT");
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(a));
        when(classStudentRepository.findByClassIdAndIsDeleted(CLASS_ID, 0)).thenReturn(List.of(
                enrollment(50L, "ENROLLED"), enrollment(51L, "ENROLLED"), enrollment(52L, "DROPPED")));
        SubmissionEntity s = submission(10, 1, 51, "SUBMITTED");
        s.setIsLate(1);
        SubmissionEntity left = submission(11, 1, 53, "GRADED");
        left.setScore(BigDecimal.ONE);
        when(submissionRepository.findByAssignmentIdAndIsDeleted(1L, 0)).thenReturn(List.of(s, left));
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(50, "Binh"), student(51, "An"),
                student(53, "Cuong")));

        SubmissionMatrixDto before = newService(DUE.minusHours(1)).getSubmissionMatrix(1L);
        assertThat(before.getRows()).extracting(r -> r.getStudentName()).containsExactly("An", "Binh", "Cuong");
        assertThat(before.getRows()).extracting(r -> r.getState()).containsExactly("LATE", "NOT_SUBMITTED", "GRADED");
        assertThat(before.getStats().getTotalStudents()).isEqualTo(3);

        SubmissionMatrixDto after = newService(DUE.plusHours(1)).getSubmissionMatrix(1L);
        assertThat(after.getRows().get(1).getState()).isEqualTo("MISSING");
        assertThat(after.getStats().getMissing()).isEqualTo(1);
        assertThat(after.getStats().getLate()).isEqualTo(1);
        assertThat(after.getStats().getGraded()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------- chấm / trả bài

    private SubmissionEntity givenSubmission(String status) {
        AssignmentEntity a = assignment(1, CLASS_ID, "PUBLISHED", "TEXT");
        a.setSyncToGradebook(1);
        SubmissionEntity s = submission(10, 1, 50, status);
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(a));
        return s;
    }

    @Test
    void grade_ok_setsGraded_updatesHistory_syncs() {
        SubmissionEntity s = givenSubmission("SUBMITTED");
        SubmissionHistoryEntity h = SubmissionHistoryEntity.builder().submissionId(10L).attemptNo(1).outcome("SUBMITTED").build();
        when(historyRepository.findBySubmissionIdOrderByAttemptNoDesc(10L)).thenReturn(List.of(h));
        SubmissionDetailDto dto = service.grade(10L, new GradeSubmissionRequest(new BigDecimal("8.5"), "<b>Tot</b>", 3L));
        assertThat(dto.getStatus()).isEqualTo("GRADED");
        assertThat(s.getGradedBy()).isEqualTo(1L);
        assertThat(h.getOutcome()).isEqualTo("GRADED");
        assertThat(h.getScore()).isEqualByComparingTo("8.5");
        verify(sync).onGraded(any(), eq(s));
        verify(auditService).success(eq(AuditActions.HOMEWORK_GRADED), eq(AuditActions.RESOURCE_SUBMISSION), eq(10L), anyMap());
    }

    @Test
    void grade_versionMismatch_isGradeConflict() {
        givenSubmission("SUBMITTED");
        assertCode(() -> service.grade(10L, new GradeSubmissionRequest(BigDecimal.ONE, null, 2L)), "GRADE_CONFLICT");
        verify(submissionRepository, never()).saveAndFlush(any());
    }

    @Test
    void grade_outOfRange_andDraft_rejected() {
        givenSubmission("SUBMITTED");
        assertCode(() -> service.grade(10L, new GradeSubmissionRequest(new BigDecimal("10.01"), null, 3L)),
                "GRADE_SCORE_OUT_OF_RANGE");
        assertCode(() -> service.grade(10L, new GradeSubmissionRequest(new BigDecimal("-1"), null, 3L)),
                "GRADE_SCORE_OUT_OF_RANGE");
        givenSubmission("DRAFT");
        assertCode(() -> service.grade(10L, new GradeSubmissionRequest(BigDecimal.ONE, null, 3L)),
                "SUBMISSION_INVALID_STATE");
    }

    @Test
    void grade_gradebookFailure_doesNotBreakGrading_andIsAudited() {
        givenSubmission("SUBMITTED");
        doThrow(new RuntimeException("ORA-00001")).when(sync).onGraded(any(), any());
        assertThat(service.grade(10L, new GradeSubmissionRequest(BigDecimal.ONE, null, 3L)).getStatus()).isEqualTo("GRADED");
        verify(auditService).failure(eq(AuditActions.HOMEWORK_GRADEBOOK_SYNC_FAILED), eq(AuditActions.RESOURCE_ASSIGNMENT),
                eq(1L), anyMap());
    }

    @Test
    void return_clearsScore_ungrades_andRejectsDraft() {
        SubmissionEntity s = givenSubmission("GRADED");
        s.setScore(BigDecimal.TEN);
        SubmissionDetailDto dto = service.returnSubmission(10L, new ReturnSubmissionRequest("Lam lai", 3L));
        assertThat(dto.getStatus()).isEqualTo("RETURNED");
        assertThat(s.getScore()).isNull();
        assertThat(s.getReturnReason()).isEqualTo("Lam lai");
        verify(sync).onUngraded(any(), eq(s));
        givenSubmission("DRAFT");
        assertCode(() -> service.returnSubmission(10L, new ReturnSubmissionRequest("x", 3L)), "SUBMISSION_INVALID_STATE");
    }

    @Test
    void submissionOfDeletedAssignment_isNotFound() {
        SubmissionEntity s = submission(10, 1, 50, "SUBMITTED");
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.empty());
        assertCode(() -> service.getSubmission(10L), "SUBMISSION_NOT_FOUND");
        assertCode(() -> service.getSubmission(404L), "SUBMISSION_NOT_FOUND");
    }

    @Test
    void feedbackFile_onDraft_rejected_onSubmitted_storedAsFeedback() {
        givenSubmission("DRAFT");
        assertCode(() -> service.uploadFeedbackFile(10L, null), "SUBMISSION_INVALID_STATE");
        givenSubmission("SUBMITTED");
        when(fileStorageService.storeFile(any(), eq("FEEDBACK"), eq(10L))).thenReturn(file(901, "FEEDBACK", 10));
        assertThat(service.uploadFeedbackFile(10L, null).getId()).isEqualTo(901L);
    }

    static ClassStudentEntity enrollment(Long studentId, String status) {
        ClassStudentEntity e = new ClassStudentEntity();
        e.setClassId(CLASS_ID);
        e.setStudentId(studentId);
        e.setStatus(status);
        return e;
    }

    static StudentEntity student(long id, String name) {
        StudentEntity s = new StudentEntity();
        s.setId(id);
        s.setFullName(name);
        s.setStudentCode("HS" + id);
        return s;
    }
}
