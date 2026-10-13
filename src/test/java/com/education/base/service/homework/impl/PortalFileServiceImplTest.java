package com.education.base.service.homework.impl;

import com.education.base.audit.AuditActions;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.impl.StudentScopeGuard;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;

import java.util.Optional;

import static com.education.base.service.homework.impl.HomeworkTestData.assignment;
import static com.education.base.service.homework.impl.HomeworkTestData.file;
import static com.education.base.service.homework.impl.HomeworkTestData.submission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalFileServiceImplTest {

    FileStorageService fileStorageService = mock(FileStorageService.class);
    AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    PortalStudentContext ctx = mock(PortalStudentContext.class);
    StudentScopeGuard scopeGuard = mock(StudentScopeGuard.class);
    AuditService auditService = mock(AuditService.class);
    PortalFileServiceImpl service = new PortalFileServiceImpl(fileStorageService, assignmentRepository,
            submissionRepository, ctx, scopeGuard, auditService);

    @BeforeEach
    void setUp() {
        when(ctx.requireCurrentStudentId()).thenReturn(42L);
        when(assignmentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(assignment(1, 7, "PUBLISHED", "BOTH")));
        when(assignmentRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(assignment(2, 7, "DRAFT", "BOTH")));
        when(assignmentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(assignment(3, 8, "PUBLISHED", "BOTH")));
        when(scopeGuard.requireEnrolled(8L)).thenThrow(new OracleBusinessException("CLASS_NOT_FOUND", "x"));
        when(submissionRepository.findById(500L)).thenReturn(Optional.of(submission(500, 1, 42, "SUBMITTED")));
        when(submissionRepository.findById(501L)).thenReturn(Optional.of(submission(501, 1, 43, "SUBMITTED")));
        when(fileStorageService.loadResource(any())).thenReturn(new ByteArrayResource(new byte[]{1}));
    }

    void given(FileEntity f) {
        when(fileStorageService.getFileEntity(f.getId())).thenReturn(f);
    }

    static void assertNotFound(ThrowableAssert.ThrowingCallable c) {
        assertThatThrownBy(c).isInstanceOf(OracleBusinessException.class)
                .extracting(e -> ((OracleBusinessException) e).getErrorCode()).isEqualTo("PORTAL_FILE_NOT_FOUND");
    }

    @Test
    void ownSubmissionAndFeedback_andVisibleAssignment_served_withSafeHeaders_andAudited() {
        given(file(10, "SUBMISSION", 500));
        given(file(11, "FEEDBACK", 500));
        given(file(12, "ASSIGNMENT", 1));
        var r = service.serve(10L, false);
        assertThat(r.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");
        assertThat(service.serve(11L, true).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(service.serve(12L, false).getStatusCode().is2xxSuccessful()).isTrue();
        verify(auditService).success(eq(AuditActions.FILE_DOWNLOADED), eq(AuditActions.RESOURCE_FILE), eq(10L), anyMap());
    }

    @Test
    void otherStudentsFile_draftOrOtherClassAssignment_otherModule_areNotFound() {
        given(file(20, "SUBMISSION", 501));
        given(file(21, "FEEDBACK", 501));
        given(file(22, "ASSIGNMENT", 2));
        given(file(23, "ASSIGNMENT", 3));
        given(file(24, "STUDENT", 42));
        assertNotFound(() -> service.serve(20L, false));
        assertNotFound(() -> service.serve(21L, false));
        assertNotFound(() -> service.serve(22L, false));
        assertNotFound(() -> service.serve(23L, false));
        assertNotFound(() -> service.serve(24L, false));
        when(fileStorageService.getFileEntity(99L)).thenThrow(new OracleBusinessException("FILE_NOT_FOUND", "x"));
        assertNotFound(() -> service.serve(99L, false));
    }

    @Test
    void scanStates() {
        FileEntity infected = file(30, "SUBMISSION", 500);
        infected.setScanStatus("INFECTED");
        FileEntity pending = file(31, "SUBMISSION", 500);
        pending.setScanStatus("PENDING");
        given(infected);
        given(pending);
        assertThatThrownBy(() -> service.serve(30L, false)).extracting(e -> ((OracleBusinessException) e).getErrorCode())
                .isEqualTo("FILE_INFECTED");
        assertThatThrownBy(() -> service.serve(31L, false)).extracting(e -> ((OracleBusinessException) e).getErrorCode())
                .isEqualTo("FILE_SCAN_PENDING");
    }
}
