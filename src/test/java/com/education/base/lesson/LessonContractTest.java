package com.education.base.lesson;

import com.education.base.audit.AuditActions;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.common.lesson.LessonConstants;
import com.education.base.common.lesson.LessonErrorCodes;
import com.education.base.dto.request.CourseUpsertRequest;
import com.education.base.dto.request.LessonReorderRequest;
import com.education.base.dto.request.LessonResourceRequest;
import com.education.base.dto.request.PortalLessonProgressRequest;
import com.education.base.security.Permissions;
import com.education.base.service.file.LessonFileModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Khoá hợp đồng GĐ3 (Luồng 0, docs/portal-p3-api.md). Bot 1 sửa test stub khi viết luật thật. */
class LessonContractTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void permissionsMatchV21_4() {
        assertThat(List.of(Permissions.COURSE_VIEW, Permissions.COURSE_CREATE, Permissions.COURSE_UPDATE,
                Permissions.COURSE_DELETE)).containsExactly("MENU_COURSE:VIEW", "MENU_COURSE:CREATE",
                "MENU_COURSE:UPDATE", "MENU_COURSE:DELETE");
        assertThat(List.of(Permissions.LESSON_VIEW, Permissions.LESSON_CREATE, Permissions.LESSON_UPDATE,
                Permissions.LESSON_DELETE)).containsExactly("MENU_LESSON:VIEW", "MENU_LESSON:CREATE",
                "MENU_LESSON:UPDATE", "MENU_LESSON:DELETE");
    }

    @Test
    void constantsMatchCheckConstraints() {
        assertThat(LessonConstants.ZONE.getId()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(LessonConstants.LessonStatus.PATTERN).isEqualTo("DRAFT|PUBLISHED");
        assertThat(LessonConstants.CourseStatus.PATTERN).isEqualTo("ACTIVE|ARCHIVED");
        assertThat(LessonConstants.ResourceType.PATTERN).isEqualTo("FILE|VIDEO|LINK|ASSIGNMENT");
        assertThat(LessonConstants.ProgressStatus.REQUEST_PATTERN).isEqualTo("IN_PROGRESS|COMPLETED");
        assertThat(LessonConstants.VIDEO_HOSTS).contains("youtu.be", "drive.google.com", "player.vimeo.com")
                .noneMatch(h -> h.startsWith("."));
        assertThat(FileConstants.Module.LESSON).isEqualTo("LESSON");
    }

    @Test
    void notFoundCodesEndWithNotFound_andNoConflictCodes() throws IllegalAccessException {
        assertThat(List.of(LessonErrorCodes.COURSE_NOT_FOUND, LessonErrorCodes.LESSON_NOT_FOUND,
                LessonErrorCodes.LESSON_SECTION_NOT_FOUND, LessonErrorCodes.LESSON_RESOURCE_NOT_FOUND))
                .allMatch(c -> c.endsWith("_NOT_FOUND"));
        for (String code : stringConstants(LessonErrorCodes.class)) {
            assertThat(code).as("GĐ3 không thêm mã 409").doesNotEndWith("_CONFLICT");
        }
    }

    @Test
    void auditActionsFitColumn() throws IllegalAccessException {
        for (String action : stringConstants(AuditActions.class)) {
            assertThat(action.length()).as(action).isLessThanOrEqualTo(50);
        }
        assertThat(AuditActions.LESSON_COMPLETED).isEqualTo("LESSON_COMPLETED");
    }

    /** Bot 1 (GĐ3 BE) đổi test này khi LessonFileModule có luật thật và là bean. */
    @Test
    void lessonDescriptorIsStubNotBean() {
        assertThat(LessonFileModule.class.isAnnotationPresent(Component.class)).isFalse();
        assertThat(FileModuleDescriptor.class.isAssignableFrom(LessonFileModule.class)).isTrue();
        LessonFileModule stub = new LessonFileModule();
        assertThat(stub.code()).isEqualTo(FileConstants.Module.LESSON);
        assertThat(stub.allowGenericUpload()).isFalse();
        assertThat(stub.canRead(null, null)).isFalse();
    }

    @Test
    void requestValidation() {
        assertThat(VALIDATOR.validate(PortalLessonProgressRequest.builder().status("COMPLETED").build())).isEmpty();
        assertThat(VALIDATOR.validate(PortalLessonProgressRequest.builder().status("NOT_STARTED").build())).isNotEmpty();
        assertThat(VALIDATOR.validate(CourseUpsertRequest.builder().courseCode("TOAN 9").courseName("x").build()))
                .isNotEmpty();
        assertThat(VALIDATOR.validate(CourseUpsertRequest.builder().courseCode("TOAN9-HK1").courseName("Toán 9").build()))
                .isEmpty();
        assertThat(VALIDATOR.validate(LessonResourceRequest.builder().resourceType("SCRIPT").build())).isNotEmpty();
        assertThat(VALIDATOR.validate(LessonReorderRequest.builder().ownerType("CLASS").ownerId(1L)
                .sections(List.of(LessonReorderRequest.SectionOrder.builder().sectionId(2L).lessonIds(List.of(3L)).build()))
                .build())).isEmpty();
    }

    private static List<String> stringConstants(Class<?> type) throws IllegalAccessException {
        List<String> out = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                f.setAccessible(true);
                out.add((String) f.get(null));
            }
        }
        return out;
    }
}
