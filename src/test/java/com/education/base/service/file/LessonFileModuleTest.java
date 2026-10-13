package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.entity.FileEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.LessonRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.service.lesson.impl.LessonFileAccess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonFileModuleTest {

    LessonRepository lessonRepository = mock(LessonRepository.class);
    LessonFileAccess lessonFileAccess = mock(LessonFileAccess.class);
    TeacherFileScope scope = mock(TeacherFileScope.class);
    LessonFileModule module = new LessonFileModule(lessonRepository, lessonFileAccess, scope);

    static AuthUserPrincipal user(String... perms) {
        return AuthUserPrincipal.builder().id(5L).username("gv").roles(List.of("ROLE_TEACHER"))
                .permissions(Set.of(perms)).build();
    }

    static FileEntity file(long id, long ref) {
        return FileEntity.builder().id(id).moduleName("LESSON").referenceId(ref).build();
    }

    static LessonEntity lesson(long id, Long courseId, Long classId, int deleted) {
        return LessonEntity.builder().id(id).courseId(courseId).classId(classId).title("Bai " + id).isDeleted(deleted)
                .build();
    }

    @Test
    void read_requiresLessonOrCourseView_andClassScope() {
        when(lessonFileAccess.lessonsUsingFile(900L)).thenReturn(List.of(lesson(11L, null, 7L, 0), lesson(12L, null, 8L, 0)));
        when(scope.classAllowed(7L)).thenReturn(false);
        when(scope.classAllowed(8L)).thenReturn(true);
        assertThat(module.canRead(user(), file(900L, 11L))).isFalse();
        assertThat(module.canRead(user(Permissions.LESSON_VIEW), file(900L, 11L))).isTrue();
        when(scope.classAllowed(8L)).thenReturn(false);
        assertThat(module.canRead(user(Permissions.LESSON_VIEW), file(900L, 11L))).isFalse();
    }

    @Test
    void read_courseLesson_noClassScopeNeeded() {
        when(lessonFileAccess.lessonsUsingFile(900L)).thenReturn(List.of(lesson(10L, 100L, null, 0), lesson(11L, null, 7L, 0)));
        assertThat(module.canRead(user(Permissions.COURSE_VIEW), file(900L, 10L))).isTrue();
    }

    @Test
    void read_noResourceLeft_fallsBackToReferenceLesson() {
        when(lessonFileAccess.lessonsUsingFile(anyLong())).thenReturn(List.of());
        when(lessonRepository.findById(11L)).thenReturn(Optional.of(lesson(11L, null, 7L, 0)));
        when(scope.classAllowed(7L)).thenReturn(true);
        assertThat(module.canRead(user(Permissions.LESSON_VIEW), file(901L, 11L))).isTrue();
        assertThat(module.canRead(user(Permissions.LESSON_VIEW), file(902L, 99L))).isFalse();
    }

    @Test
    void genericUploadAndDelete_blocked() {
        assertThat(module.allowGenericUpload()).isFalse();
        assertThat(module.canUpload(user(Permissions.LESSON_UPDATE), 11L)).isFalse();
        assertThat(module.canDelete(user(Permissions.LESSON_DELETE), file(900L, 11L))).isFalse();
        assertThat(module.code()).isEqualTo(FileConstants.Module.LESSON);
    }

    @Test
    void reference_usableAndDescribed() {
        when(lessonRepository.findById(11L)).thenReturn(Optional.of(lesson(11L, null, 7L, 0)));
        when(lessonRepository.findById(12L)).thenReturn(Optional.of(lesson(12L, null, 7L, 1)));
        module.assertReferenceUsable(11L);
        assertThatThrownBy(() -> module.assertReferenceUsable(12L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("FILE_REFERENCE_INACTIVE");
        assertThatThrownBy(() -> module.assertReferenceUsable(13L)).isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("FILE_REFERENCE_NOT_FOUND");
        FileReferenceInfo info = module.describeReference(11L);
        assertThat(info.label()).isEqualTo("Bài học: Bai 11");
        assertThat(info.uiRoute()).isEqualTo("/academic/lessons?lesson=11");
        assertThat(info.state()).isEqualTo(FileConstants.RefState.ACTIVE);
        assertThat(module.describeReference(12L).state()).isEqualTo(FileConstants.RefState.DELETED);
    }
}
