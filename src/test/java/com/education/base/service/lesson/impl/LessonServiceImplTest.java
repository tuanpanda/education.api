package com.education.base.service.lesson.impl;

import com.education.base.common.HtmlContentSanitizer;
import com.education.base.dto.request.LessonReorderRequest;
import com.education.base.dto.request.LessonResourceReorderRequest;
import com.education.base.dto.request.LessonResourceRequest;
import com.education.base.dto.request.LessonSectionUpsertRequest;
import com.education.base.dto.request.LessonUpsertRequest;
import com.education.base.dto.response.ClassLessonProgressDto;
import com.education.base.dto.response.LessonDto;
import com.education.base.dto.response.LessonResourceDto;
import com.education.base.dto.response.LessonTreeDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonProgressEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.security.Permissions;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.support.TestSecurityContexts;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Set;

import static com.education.base.service.lesson.impl.LessonFakeDb.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonServiceImplTest {

    static final long TEACHER = 5L;
    static final long OTHER_TEACHER = 6L;
    static final long CLASS_A = 7L;
    static final long CLASS_B = 8L;
    static final long COURSE = 100L;

    LessonFakeDb db = new LessonFakeDb();
    FileStorageService fileStorageService = mock(FileStorageService.class);
    FileLifecycleService fileLifecycleService = mock(FileLifecycleService.class);
    AuditService auditService = mock(AuditService.class);
    LessonServiceImpl service = new LessonServiceImpl(db.sectionRepository, db.lessonRepository, db.resourceRepository,
            db.progressRepository, db.classRepository, db.sessionRepository, db.classStudentRepository,
            db.studentRepository, db.assignmentRepository, fileStorageService, fileLifecycleService,
            mock(FileMapper.class), db.guard, new HtmlContentSanitizer(), new VideoEmbedResolverImpl(), auditService,
            db.access, db.catalog, db.clock);

    LessonSectionEntity secA1;
    LessonSectionEntity secA2;
    LessonSectionEntity secCourse;
    LessonEntity a1;
    LessonEntity a2;
    LessonEntity c1;

    @BeforeEach
    void setUp() {
        db.course(COURSE, "TOAN9", "ACTIVE");
        db.clazz(CLASS_A, TEACHER, COURSE);
        db.clazz(CLASS_B, OTHER_TEACHER, null);
        secCourse = db.section(1L, COURSE, null, 0);
        secA1 = db.section(2L, null, CLASS_A, 0);
        secA2 = db.section(3L, null, CLASS_A, 1);
        db.section(4L, null, CLASS_B, 0);
        c1 = db.lesson(10L, secCourse, 0);
        a1 = db.lesson(11L, secA1, 0);
        a2 = db.lesson(12L, secA1, 1);
        db.lesson(13L, db.sections.get(4L), 0);
        TestSecurityContexts.loginAdmin(1L);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    static void assertCode(ThrowableAssert.ThrowingCallable c, String code) {
        assertThatThrownBy(c).isInstanceOfAny(OracleBusinessException.class, ForbiddenException.class)
                .extracting("errorCode").isEqualTo(code);
    }

    void loginTeacher(long id, String... perms) {
        TestSecurityContexts.login(id, List.of("ROLE_TEACHER"), Set.of(perms));
    }

    static LessonUpsertRequest upsert(long sectionId, String title) {
        return LessonUpsertRequest.builder().sectionId(sectionId).title(title).build();
    }

    // ============================================================================================ cây / quyền

    @Test
    void classTree_includesInheritedCourseSectionsFirst() {
        LessonTreeDto tree = service.getTree("CLASS", CLASS_A);
        assertThat(tree.getSections()).extracting("id").containsExactly(2L, 3L);
        assertThat(tree.getInheritedSections()).extracting("id").containsExactly(1L);
        assertThat(tree.getCourseId()).isEqualTo(COURSE);
        assertThat(tree.getSections().get(0).getLessons()).extracting("id").containsExactly(11L, 12L);
        assertThat(tree.getCanManage()).isTrue();
    }

    @Test
    void teacherOfOtherClass_is403NotClassTeacher() {
        loginTeacher(OTHER_TEACHER, Permissions.LESSON_VIEW, Permissions.LESSON_CREATE, Permissions.LESSON_UPDATE);
        assertCode(() -> service.getTree("CLASS", CLASS_A), "NOT_CLASS_TEACHER");
        assertCode(() -> service.getById(11L), "NOT_CLASS_TEACHER");
        assertCode(() -> service.create(upsert(2L, "x")), "NOT_CLASS_TEACHER");
        assertCode(() -> service.getClassProgress(CLASS_A), "NOT_CLASS_TEACHER");
    }

    @Test
    void teacher_ownClass_canCreateAndEdit_butNotDelete_andCourseIsReadOnly() {
        loginTeacher(TEACHER, Permissions.LESSON_VIEW, Permissions.LESSON_CREATE, Permissions.LESSON_UPDATE,
                Permissions.COURSE_VIEW);
        LessonDto created = service.create(upsert(2L, "Bai moi"));
        assertThat(created.getStatus()).isEqualTo("DRAFT");
        assertThat(created.getSortOrder()).isEqualTo(2);
        assertThat(service.update(created.getId(), upsert(2L, "Bai moi 2")).getTitle()).isEqualTo("Bai moi 2");
        assertCode(() -> service.softDelete(created.getId()), "FORBIDDEN");
        // khoá mẫu: chỉ xem
        assertThat(service.getTree("COURSE", COURSE).getCanManage()).isFalse();
        assertThat(service.getById(10L).getCanManage()).isFalse();
        assertCode(() -> service.create(upsert(1L, "x")), "FORBIDDEN");
        assertCode(() -> service.publish(10L), "FORBIDDEN");
    }

    @Test
    void notFound_codes() {
        assertCode(() -> service.getById(999L), "LESSON_NOT_FOUND");
        assertCode(() -> service.updateSection(999L, LessonSectionUpsertRequest.builder().title("x").build()),
                "LESSON_SECTION_NOT_FOUND");
        assertCode(() -> service.deleteResource(999L), "LESSON_RESOURCE_NOT_FOUND");
        assertCode(() -> service.getTree("COURSE", 999L), "COURSE_NOT_FOUND");
        assertCode(() -> service.getTree("SCHOOL", 1L), "LESSON_INVALID");
    }

    // ============================================================================================ chương / bài

    @Test
    void createSection_appendsAtEnd_andLimit() {
        var dto = service.createSection(LessonSectionUpsertRequest.builder().ownerType("class").ownerId(CLASS_A)
                .title(" Chuong 3 ").build());
        assertThat(dto.getSortOrder()).isEqualTo(2);
        assertThat(dto.getTitle()).isEqualTo("Chuong 3");
        assertThat(dto.getOwnerType()).isEqualTo("CLASS");
        for (int i = 0; i < 97; i++) {
            service.createSection(LessonSectionUpsertRequest.builder().ownerType("CLASS").ownerId(CLASS_A).title("s").build());
        }
        assertCode(() -> service.createSection(LessonSectionUpsertRequest.builder().ownerType("CLASS").ownerId(CLASS_A)
                .title("s").build()), "LESSON_INVALID");
    }

    @Test
    void deleteSection_withLessons_isNotEmpty() {
        assertCode(() -> service.deleteSection(2L), "LESSON_SECTION_NOT_EMPTY");
        service.deleteSection(3L);
        assertThat(db.sections.get(3L).getIsDeleted()).isEqualTo(1);
    }

    @Test
    void create_sessionRules() {
        db.session(50L, CLASS_A, NOW.plusDays(1), "SCHEDULED");
        db.session(51L, CLASS_B, NOW.plusDays(1), "SCHEDULED");
        db.session(52L, CLASS_A, NOW.plusDays(1), "CANCELLED");
        LessonUpsertRequest ok = upsert(2L, "x");
        ok.setSessionId(50L);
        assertThat(service.create(ok).getSessionDate()).isEqualTo(NOW.plusDays(1).toLocalDate());
        LessonUpsertRequest otherClass = upsert(2L, "x");
        otherClass.setSessionId(51L);
        assertCode(() -> service.create(otherClass), "LESSON_SESSION_INVALID");
        LessonUpsertRequest cancelled = upsert(2L, "x");
        cancelled.setSessionId(52L);
        assertCode(() -> service.create(cancelled), "LESSON_SESSION_INVALID");
        LessonUpsertRequest courseLesson = upsert(1L, "x");
        courseLesson.setSessionId(50L);
        assertCode(() -> service.create(courseLesson), "LESSON_SESSION_INVALID");
    }

    @Test
    void create_sanitizesContent() {
        LessonUpsertRequest r = upsert(2L, "x");
        r.setContent("<p>Xin chao</p><script>alert(1)</script><img src=x onerror=alert(1)>");
        LessonDto dto = service.create(r);
        assertThat(dto.getContent()).contains("<p>Xin chao</p>").doesNotContain("script").doesNotContain("onerror");
    }

    @Test
    void update_moveToSectionOfOtherOwner_isInvalid() {
        assertCode(() -> service.update(11L, upsert(1L, "x")), "LESSON_INVALID");
        assertCode(() -> service.update(11L, upsert(4L, "x")), "LESSON_INVALID");
        LessonDto moved = service.update(11L, upsert(3L, "x"));
        assertThat(moved.getSectionId()).isEqualTo(3L);
    }

    @Test
    void publish_unpublish_states() {
        LessonDto draft = service.create(upsert(2L, "x"));
        assertCode(() -> service.unpublish(draft.getId()), "LESSON_INVALID_STATE");
        LessonDto pub = service.publish(draft.getId());
        assertThat(pub.getStatus()).isEqualTo("PUBLISHED");
        assertThat(pub.getPublishedAt()).isEqualTo(NOW);
        assertThat(pub.getAvailableNow()).isTrue();
        assertCode(() -> service.publish(draft.getId()), "LESSON_INVALID_STATE");
        assertThat(service.unpublish(draft.getId()).getAvailableNow()).isFalse();
    }

    @Test
    void softDelete_removesLessonAndResources() {
        db.fileResource(70L, 11L, 900L);
        service.softDelete(11L);
        assertThat(db.lessons.get(11L).getIsDeleted()).isEqualTo(1);
        assertThat(db.resources.get(70L).getIsDeleted()).isEqualTo(1);
        verify(fileLifecycleService).softDelete(eq(900L), anyString());
    }

    // ============================================================================================ sắp xếp

    static LessonReorderRequest.SectionOrder so(long sectionId, Long... lessonIds) {
        return LessonReorderRequest.SectionOrder.builder().sectionId(sectionId).lessonIds(List.of(lessonIds)).build();
    }

    @Test
    void reorder_movesLessonsAcrossSections() {
        LessonTreeDto tree = service.reorder(LessonReorderRequest.builder().ownerType("CLASS").ownerId(CLASS_A)
                .sections(List.of(so(3L, 12L), so(2L, 11L))).build());
        assertThat(tree.getSections()).extracting("id").containsExactly(3L, 2L);
        assertThat(db.lessons.get(12L).getSectionId()).isEqualTo(3L);
        assertThat(db.lessons.get(12L).getSortOrder()).isZero();
        assertThat(db.sections.get(3L).getSortOrder()).isZero();
    }

    @Test
    void reorder_missingExtraDuplicateOrOtherOwner_isInvalid() {
        assertCode(() -> service.reorder(LessonReorderRequest.builder().ownerType("CLASS").ownerId(CLASS_A)
                .sections(List.of(so(2L, 11L), so(3L))).build()), "LESSON_REORDER_INVALID");
        assertCode(() -> service.reorder(LessonReorderRequest.builder().ownerType("CLASS").ownerId(CLASS_A)
                .sections(List.of(so(2L, 11L, 12L, 13L), so(3L))).build()), "LESSON_REORDER_INVALID");
        assertCode(() -> service.reorder(LessonReorderRequest.builder().ownerType("CLASS").ownerId(CLASS_A)
                .sections(List.of(so(2L, 11L, 12L, 11L), so(3L))).build()), "LESSON_REORDER_INVALID");
        assertCode(() -> service.reorder(LessonReorderRequest.builder().ownerType("CLASS").ownerId(CLASS_A)
                .sections(List.of(so(2L, 11L, 12L))).build()), "LESSON_REORDER_INVALID");
        assertCode(() -> service.reorder(LessonReorderRequest.builder().ownerType("CLASS").ownerId(CLASS_A)
                .sections(List.of(so(2L, 11L, 12L), so(3L), so(1L, 10L))).build()), "LESSON_REORDER_INVALID");
    }

    // ============================================================================================ tài nguyên

    static LessonResourceRequest res(String type, String url, Long assignmentId) {
        return LessonResourceRequest.builder().resourceType(type).url(url).assignmentId(assignmentId).build();
    }

    @Test
    void addResource_videoLinkAssignment() {
        LessonResourceDto v = service.addResource(11L, res("VIDEO", "https://youtu.be/dQw4w9WgXcQ?t=90", null));
        assertThat(v.getEmbedUrl()).isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?start=90");
        assertThat(v.getProvider()).isEqualTo("YOUTUBE");
        LessonResourceDto link = service.addResource(11L, res("LINK", "https://example.com/a", null));
        assertThat(link.getProvider()).isEqualTo("OTHER");
        assertThat(link.getTitle()).isEqualTo("example.com");
        assertThat(link.getSortOrder()).isEqualTo(1);
        db.assignment(300L, CLASS_A, "PUBLISHED");
        LessonResourceDto a = service.addResource(11L, res("ASSIGNMENT", null, 300L));
        assertThat(a.getAssignmentTitle()).isEqualTo("BT 300");
    }

    @Test
    void addResource_invalidInputs() {
        assertCode(() -> service.addResource(11L, res("LINK", "javascript:alert(1)", null)), "LESSON_URL_INVALID");
        assertCode(() -> service.addResource(11L, res("LINK", "data:text/html,x", null)), "LESSON_URL_INVALID");
        assertCode(() -> service.addResource(11L, res("LINK", "file:///c:/x", null)), "LESSON_URL_INVALID");
        assertCode(() -> service.addResource(11L, res("VIDEO", "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ", null)),
                "LESSON_VIDEO_URL_NOT_ALLOWED");
        assertCode(() -> service.addResource(11L, res("FILE", null, null)), "LESSON_RESOURCE_INVALID");
        db.assignment(301L, CLASS_B, "PUBLISHED");
        assertCode(() -> service.addResource(11L, res("ASSIGNMENT", null, 301L)), "LESSON_ASSIGNMENT_INVALID");
        db.assignment(302L, CLASS_A, "PUBLISHED").setIsDeleted(1);
        assertCode(() -> service.addResource(11L, res("ASSIGNMENT", null, 302L)), "LESSON_ASSIGNMENT_INVALID");
        db.assignment(303L, CLASS_A, "PUBLISHED");
        assertCode(() -> service.addResource(10L, res("ASSIGNMENT", null, 303L)), "LESSON_ASSIGNMENT_INVALID");
    }

    @Test
    void addResource_maxPerLesson() {
        for (int i = 0; i < 30; i++) {
            service.addResource(11L, res("LINK", "https://example.com/" + i, null));
        }
        assertCode(() -> service.addResource(11L, res("LINK", "https://example.com/x", null)), "LESSON_RESOURCE_INVALID");
    }

    @Test
    void uploadFile_storesInLessonModule() {
        FileEntity stored = FileEntity.builder().id(901L).originalName("slide.pptx").build();
        when(fileStorageService.storeFile(any(), eq("LESSON"), eq(11L))).thenReturn(stored);
        MockMultipartFile f = new MockMultipartFile("file", "slide.pptx", "application/octet-stream", new byte[]{1});
        LessonResourceDto dto = service.uploadResourceFile(11L, f, null);
        assertThat(dto.getResourceType()).isEqualTo("FILE");
        assertThat(dto.getTitle()).isEqualTo("slide.pptx");
        assertThat(db.resources.get(dto.getId()).getFileId()).isEqualTo(901L);
    }

    @Test
    void updateResource_cannotChangeType_fileOnlyTitle() {
        db.fileResource(70L, 11L, 900L);
        assertCode(() -> service.updateResource(70L, res("LINK", "https://example.com", null)), "LESSON_RESOURCE_INVALID");
        LessonResourceRequest r = res("FILE", "https://ignored.example.com", null);
        r.setTitle("De cuong");
        service.updateResource(70L, r);
        assertThat(db.resources.get(70L).getTitle()).isEqualTo("De cuong");
        assertThat(db.resources.get(70L).getUrl()).isNull();
    }

    @Test
    void deleteResource_sharedFile_softDeletedOnlyWhenLastUse() {
        db.fileResource(70L, 11L, 900L);
        db.fileResource(71L, 12L, 900L);
        service.deleteResource(70L);
        verify(fileLifecycleService, never()).softDelete(anyLong(), anyString());
        service.deleteResource(71L);
        verify(fileLifecycleService).softDelete(eq(900L), anyString());
    }

    @Test
    void reorderResources_mustMatchExactly() {
        db.videoResource(70L, 11L);
        db.fileResource(71L, 11L, 900L);
        db.videoResource(72L, 12L);
        assertThat(service.reorderResources(11L, new LessonResourceReorderRequest(List.of(71L, 70L))))
                .extracting("id").containsExactly(71L, 70L);
        assertCode(() -> service.reorderResources(11L, new LessonResourceReorderRequest(List.of(71L))),
                "LESSON_REORDER_INVALID");
        assertCode(() -> service.reorderResources(11L, new LessonResourceReorderRequest(List.of(71L, 72L))),
                "LESSON_REORDER_INVALID");
    }

    // ============================================================================================ tiến độ

    @Test
    void classProgress_onlyAvailableLessons_andPercent() {
        a2.setAvailableFrom(NOW.plusDays(1)); // chưa mở -> không tính
        db.enroll(1L, CLASS_A, 42L);
        db.enroll(2L, CLASS_A, 43L);
        db.progress.put(1L, LessonProgressEntity.builder().id(1L).lessonId(11L).studentId(42L).status("COMPLETED")
                .lastViewedAt(NOW.minusHours(1)).build());
        db.progress.put(2L, LessonProgressEntity.builder().id(2L).lessonId(12L).studentId(42L).status("COMPLETED")
                .build());
        ClassLessonProgressDto dto = service.getClassProgress(CLASS_A);
        assertThat(dto.getLessons()).extracting("lessonId").containsExactly(10L, 11L);
        assertThat(dto.getLessons()).extracting("source").containsExactly("COURSE", "CLASS");
        ClassLessonProgressDto.StudentRow me = dto.getRows().stream().filter(r -> r.getStudentId() == 42L).findFirst()
                .orElseThrow();
        assertThat(me.getCompletedCount()).isEqualTo(1);
        assertThat(me.getTotalLessons()).isEqualTo(2);
        assertThat(me.getPercent()).isEqualTo(50);
        assertThat(me.getStatuses()).containsEntry(10L, "NOT_STARTED").containsEntry(11L, "COMPLETED");
        assertThat(dto.getLessons().get(1).getCompletedCount()).isEqualTo(1);
    }

    @Test
    void manageableClasses_teacherSeesOwnOnly() {
        loginTeacher(TEACHER, Permissions.LESSON_VIEW);
        when(db.classRepository.findByIdInAndIsDeletedOrderByClassCode(any(), any())).thenAnswer(i -> {
            java.util.Collection<Long> ids = i.getArgument(0);
            return db.classes.values().stream().filter(c -> ids.contains(c.getId())).toList();
        });
        assertThat(service.listManageableClasses()).extracting("id").containsExactly(CLASS_A);
    }

    @SuppressWarnings("unused")
    private ClassEntity classA() {
        return db.classes.get(CLASS_A);
    }
}
