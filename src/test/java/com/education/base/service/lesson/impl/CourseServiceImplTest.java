package com.education.base.service.lesson.impl;

import com.education.base.common.HtmlContentSanitizer;
import com.education.base.dto.request.CourseUpsertRequest;
import com.education.base.dto.response.CourseCopyResultDto;
import com.education.base.dto.response.CourseDto;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonResourceEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.security.Permissions;
import com.education.base.service.AuditService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.education.base.service.lesson.impl.LessonServiceImplTest.assertCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CourseServiceImplTest {

    LessonFakeDb db = new LessonFakeDb();
    FileLifecycleService fileLifecycleService = mock(FileLifecycleService.class);
    CourseServiceImpl service = new CourseServiceImpl(db.courseRepository, db.classRepository,
            db.classStudentRepository, db.sectionRepository, db.lessonRepository, db.resourceRepository,
            new HtmlContentSanitizer(), fileLifecycleService, mock(AuditService.class), db.access, db.catalog, db.clock);

    @BeforeEach
    void setUp() {
        TestSecurityContexts.loginAdmin(1L);
        db.course(100L, "TOAN9", "ACTIVE");
        db.course(101L, "VAN9", "ARCHIVED");
        db.clazz(7L, 5L, null);
        db.clazz(8L, 6L, 101L);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    static CourseUpsertRequest req(String code) {
        return CourseUpsertRequest.builder().courseCode(code).courseName("Khoa").description("<p>a</p><script>x</script>")
                .build();
    }

    @Test
    void create_duplicateCodeIgnoreCase_andDefaults() {
        assertCode(() -> service.create(req("toan9")), "COURSE_CODE_DUPLICATE");
        CourseDto dto = service.create(req(" LY9 "));
        assertThat(dto.getCourseCode()).isEqualTo("LY9");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getDescription()).isEqualTo("<p>a</p>");
        assertThat(dto.getCanManage()).isTrue();
    }

    @Test
    void update_sameCodeOtherCase_ok_butOtherCourseCode_duplicate() {
        assertThat(service.update(100L, req("Toan9")).getCourseCode()).isEqualTo("Toan9");
        assertCode(() -> service.update(100L, req("van9")), "COURSE_CODE_DUPLICATE");
        assertCode(() -> service.update(999L, req("X")), "COURSE_NOT_FOUND");
    }

    @Test
    void delete_inUse_thenCascadeSoftDelete() {
        assertCode(() -> service.softDelete(101L), "COURSE_IN_USE");
        LessonSectionEntity s = db.section(1L, 100L, null, 0);
        LessonEntity l = db.lesson(10L, s, 0);
        db.fileResource(70L, 10L, 900L);
        db.fileResource(71L, 10L, 901L);
        // bản chép ở lớp dùng chung file 901 -> giữ file
        db.section(2L, null, 7L, 0);
        db.fileResource(72L, db.lesson(11L, db.sections.get(2L), 0).getId(), 901L);
        service.softDelete(100L);
        assertThat(db.courses.get(100L).getIsDeleted()).isEqualTo(1);
        assertThat(s.getIsDeleted()).isEqualTo(1);
        assertThat(l.getIsDeleted()).isEqualTo(1);
        assertThat(db.resources.get(70L).getIsDeleted()).isEqualTo(1);
        verify(fileLifecycleService).softDelete(eq(900L), anyString());
        verify(fileLifecycleService, never()).softDelete(eq(901L), anyString());
    }

    @Test
    void link_rules() {
        assertCode(() -> service.linkClass(101L, 7L), "COURSE_INVALID_STATE"); // ARCHIVED
        assertCode(() -> service.linkClass(100L, 8L), "COURSE_INVALID_STATE"); // lớp đã gắn khoá khác
        assertCode(() -> service.linkClass(100L, 999L), "CLASS_NOT_FOUND");
        assertThat(service.linkClass(100L, 7L).getClassId()).isEqualTo(7L);
        assertThat(db.classes.get(7L).getCourseId()).isEqualTo(100L);
        assertThat(service.linkClass(100L, 7L).getClassId()).isEqualTo(7L); // gắn lại = không đổi
        assertThat(service.listClasses(100L)).extracting("classId").containsExactly(7L);
        assertCode(() -> service.unlinkClass(101L, 7L), "COURSE_INVALID_STATE");
        service.unlinkClass(100L, 7L);
        assertThat(db.classes.get(7L).getCourseId()).isNull();
        // lớp ARCHIVED đang gắn vẫn gỡ được
        service.unlinkClass(101L, 8L);
    }

    @Test
    void copyToClass_countsDraftSharedFile_noAssignment() {
        LessonSectionEntity s1 = db.section(1L, 100L, null, 0);
        LessonSectionEntity s2 = db.section(2L, 100L, null, 1);
        LessonEntity l1 = db.lesson(10L, s1, 0);
        db.lesson(11L, s1, 1);
        db.lesson(12L, s2, 0);
        db.fileResource(70L, l1.getId(), 900L);
        db.videoResource(71L, l1.getId());
        db.section(3L, null, 7L, 0); // lớp đã có 1 chương riêng

        CourseCopyResultDto r = service.copyToClass(100L, 7L);
        assertThat(r.getSectionCount()).isEqualTo(2);
        assertThat(r.getLessonCount()).isEqualTo(3);
        assertThat(r.getResourceCount()).isEqualTo(2);

        List<LessonSectionEntity> classSections = db.sections.values().stream()
                .filter(s -> Long.valueOf(7L).equals(s.getClassId())).toList();
        assertThat(classSections).hasSize(3);
        assertThat(classSections.stream().filter(s -> s.getSourceSectionId() != null).map(LessonSectionEntity::getSortOrder))
                .containsExactlyInAnyOrder(1, 2);
        List<LessonEntity> copies = db.lessons.values().stream().filter(l -> l.getSourceLessonId() != null).toList();
        assertThat(copies).hasSize(3).allMatch(l -> "DRAFT".equals(l.getStatus()) && l.getClassId() == 7L
                && l.getCourseId() == null && l.getPublishedAt() == null);
        LessonEntity copyOfL1 = copies.stream().filter(l -> l.getSourceLessonId() == 10L).findFirst().orElseThrow();
        List<LessonResourceEntity> copiedRes = db.resources.values().stream()
                .filter(x -> x.getLessonId().equals(copyOfL1.getId())).toList();
        assertThat(copiedRes).extracting("fileId").contains(900L);
        assertThat(db.classes.get(7L).getCourseId()).isNull(); // không đổi COURSE_ID
    }

    @Test
    void copyToClass_teacherOfOtherClass_403() {
        TestSecurityContexts.login(6L, List.of("ROLE_TEACHER"), Set.of(Permissions.LESSON_CREATE));
        assertCode(() -> service.copyToClass(100L, 7L), "NOT_CLASS_TEACHER");
        verify(db.sectionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getById_counts() {
        db.lesson(10L, db.section(1L, 100L, null, 0), 0);
        assertThat(service.getById(100L).getClassCount()).isZero();
        assertThat(service.getById(101L).getClassCount()).isEqualTo(1);
        assertCode(() -> service.getById(999L), "COURSE_NOT_FOUND");
        verify(fileLifecycleService, never()).softDelete(anyLong(), anyString());
    }
}
