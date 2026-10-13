package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.education.base.service.homework.impl.HomeworkTestData.assignment;
import static com.education.base.service.homework.impl.HomeworkTestData.submission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** GĐ2: luật 3 descriptor ASSIGNMENT / SUBMISSION / FEEDBACK. */
class HomeworkFileModulesTest {

    AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    ClassRepository classRepository = mock(ClassRepository.class);
    TeacherFileScope scope = mock(TeacherFileScope.class);
    HomeworkFileRefs refs = new HomeworkFileRefs(assignmentRepository, submissionRepository);
    AssignmentFileModule assignmentModule = new AssignmentFileModule(refs, classRepository, scope);
    SubmissionFileModule submissionModule = new SubmissionFileModule(refs, scope);
    FeedbackFileModule feedbackModule = new FeedbackFileModule(refs, scope);

    static AuthUserPrincipal user(String... perms) {
        return AuthUserPrincipal.builder().id(5L).username("gv").role("ROLE_TEACHER").permissions(java.util.Set.of(perms))
                .build();
    }

    @BeforeEach
    void setUp() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment(1, 7, "PUBLISHED", "BOTH")));
        var deleted = assignment(2, 8, "PUBLISHED", "BOTH");
        deleted.setIsDeleted(1);
        when(assignmentRepository.findById(2L)).thenReturn(Optional.of(deleted));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission(10, 1, 42, "SUBMITTED")));
        when(scope.classAllowed(7L)).thenReturn(true);
        when(scope.classAllowed(8L)).thenReturn(false);
        ClassEntity c = new ClassEntity();
        c.setClassCode("L7");
        when(classRepository.findByIdAndIsDeleted(7L, 0)).thenReturn(Optional.of(c));
    }

    static FileEntity f(String module, Long ref) {
        return FileEntity.builder().id(100L).moduleName(module).referenceId(ref).build();
    }

    @Test
    void allThreeAreBeans_noGenericUpload() {
        for (FileModuleDescriptor d : List.<FileModuleDescriptor>of(assignmentModule, submissionModule, feedbackModule)) {
            assertThat(d.getClass().isAnnotationPresent(Component.class)).isTrue();
            assertThat(d.allowGenericUpload()).isFalse();
        }
        assertThat(List.of(assignmentModule.code(), submissionModule.code(), feedbackModule.code()))
                .containsExactly(FileConstants.Module.ASSIGNMENT, FileConstants.Module.SUBMISSION,
                        FileConstants.Module.FEEDBACK);
    }

    @Test
    void assignment_readNeedsViewAndScope_uploadNeedsUpdateOrCreate() {
        AuthUserPrincipal viewer = user(Permissions.HOMEWORK_VIEW);
        assertThat(assignmentModule.canRead(viewer, f("ASSIGNMENT", 1L))).isTrue();
        assertThat(assignmentModule.canRead(viewer, f("ASSIGNMENT", 2L))).isFalse();
        assertThat(assignmentModule.canRead(user(), f("ASSIGNMENT", 1L))).isFalse();
        assertThat(assignmentModule.canUpload(viewer, 1L)).isFalse();
        assertThat(assignmentModule.canUpload(user(Permissions.HOMEWORK_UPDATE), 1L)).isTrue();
        assertThat(assignmentModule.canDelete(user(Permissions.HOMEWORK_CREATE), f("ASSIGNMENT", 1L))).isTrue();
        assertThat(assignmentModule.canDelete(user(Permissions.HOMEWORK_UPDATE), f("ASSIGNMENT", 2L))).isFalse();
    }

    @Test
    void assignment_reference_andDescribe() {
        assignmentModule.assertReferenceUsable(1L);
        assertThatThrownBy(() -> assignmentModule.assertReferenceUsable(2L)).isInstanceOf(OracleBusinessException.class)
                .extracting(e -> ((OracleBusinessException) e).getErrorCode()).isEqualTo("FILE_REFERENCE_INACTIVE");
        assertThatThrownBy(() -> assignmentModule.assertReferenceUsable(3L))
                .extracting(e -> ((OracleBusinessException) e).getErrorCode()).isEqualTo("FILE_REFERENCE_NOT_FOUND");
        var info = assignmentModule.describeReference(1L);
        assertThat(info.label()).isEqualTo("Bài tập: Bai 1 (L7)");
        assertThat(info.uiRoute()).isEqualTo("/academic/homework?id=1");
    }

    @Test
    void submission_staffReadOnly_ownerIsStudent() {
        AuthUserPrincipal all = user(Permissions.HOMEWORK_VIEW, Permissions.HOMEWORK_UPDATE, Permissions.HOMEWORK_GRADE);
        assertThat(submissionModule.canRead(all, f("SUBMISSION", 10L))).isTrue();
        assertThat(submissionModule.canUpload(all, 10L)).isFalse();
        assertThat(submissionModule.canDelete(all, f("SUBMISSION", 10L))).isFalse();
        assertThat(submissionModule.ownerStudentId(10L)).contains(42L);
        assertThat(submissionModule.describeReference(10L).uiRoute()).isEqualTo("/academic/homework?id=1&submission=10");
        assertThat(submissionModule.canRead(all, f("SUBMISSION", 404L))).isFalse();
    }

    @Test
    void feedback_readWithView_genericWriteDenied() {
        AuthUserPrincipal all = user(Permissions.HOMEWORK_VIEW, Permissions.HOMEWORK_GRADE);
        assertThat(feedbackModule.canRead(all, f("FEEDBACK", 10L))).isTrue();
        assertThat(feedbackModule.canUpload(all, 10L)).isFalse();
        assertThat(feedbackModule.canDelete(all, f("FEEDBACK", 10L))).isFalse();
        assertThat(feedbackModule.ownerStudentId(10L)).isEmpty();
        assertThat(feedbackModule.canRead(null, f("FEEDBACK", 10L))).isFalse();
    }

    @Test
    void registryAcceptsHomeworkModules() {
        FileModuleRegistry registry = new FileModuleRegistry(List.of(new CommonFileModule(), assignmentModule,
                submissionModule, feedbackModule));
        assertThat(registry.find("submission")).isPresent();
        assertThat(registry.require("FEEDBACK").code()).isEqualTo("FEEDBACK");
    }
}
