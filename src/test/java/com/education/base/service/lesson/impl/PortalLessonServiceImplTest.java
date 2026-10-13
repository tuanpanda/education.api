package com.education.base.service.lesson.impl;

import com.education.base.dto.request.PortalLessonProgressRequest;
import com.education.base.dto.response.PortalLessonClassDto;
import com.education.base.dto.response.PortalLessonDetailDto;
import com.education.base.dto.response.PortalLessonProgressDto;
import com.education.base.dto.response.PortalLessonTreeDto;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonProgressEntity;
import com.education.base.entity.LessonSectionEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.impl.StudentScopeGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.education.base.service.lesson.impl.LessonFakeDb.NOW;
import static com.education.base.service.lesson.impl.LessonServiceImplTest.assertCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalLessonServiceImplTest {

    static final long ME = 42L;
    static final long CLASS_A = 7L;   // HS học, gắn khoá 100
    static final long CLASS_B = 8L;   // HS không học
    static final long CLASS_C = 9L;   // HS học, không gắn khoá

    LessonFakeDb db = new LessonFakeDb();
    PortalStudentContext ctx = mock(PortalStudentContext.class);
    StudentScopeGuard scopeGuard = mock(StudentScopeGuard.class);
    AuditService auditService = mock(AuditService.class);
    FileStorageService fileStorageService = mock(FileStorageService.class);
    PortalLessonServiceImpl service;

    LessonEntity course1;
    LessonEntity a1;
    LessonEntity a2Future;
    LessonEntity aDraft;
    LessonEntity b1;
    LessonEntity c1;

    @BeforeEach
    void setUp() {
        db.course(100L, "TOAN9", "ACTIVE");
        db.course(101L, "VAN9", "ACTIVE");
        db.clazz(CLASS_A, 5L, 100L);
        db.clazz(CLASS_B, 6L, 101L);
        db.clazz(CLASS_C, 5L, null);
        db.enroll(1L, CLASS_A, ME);
        db.enroll(2L, CLASS_C, ME);
        LessonSectionEntity sc = db.section(1L, 100L, null, 0);
        LessonSectionEntity sb = db.section(2L, 101L, null, 0);
        LessonSectionEntity sa = db.section(3L, null, CLASS_A, 0);
        LessonSectionEntity sB = db.section(4L, null, CLASS_B, 0);
        LessonSectionEntity sC = db.section(5L, null, CLASS_C, 0);
        course1 = db.lesson(10L, sc, 0);
        db.lesson(14L, sb, 0); // khoá 101 (gắn lớp B)
        a1 = db.lesson(11L, sa, 0);
        a2Future = db.lesson(12L, sa, 1);
        a2Future.setAvailableFrom(NOW.plusHours(1));
        aDraft = db.lesson(13L, sa, 2);
        aDraft.setStatus("DRAFT");
        b1 = db.lesson(15L, sB, 0);
        c1 = db.lesson(16L, sC, 0);
        when(ctx.requireCurrentStudentId()).thenReturn(ME);
        when(scopeGuard.requireEnrolled(anyLong())).thenAnswer(i -> {
            long id = i.getArgument(0);
            return db.enrollments.values().stream().filter(e -> e.getClassId() == id && e.getStudentId() == ME)
                    .findFirst().orElseThrow(() -> new OracleBusinessException("CLASS_NOT_FOUND", "x"));
        });
        service = new PortalLessonServiceImpl(db.lessonRepository, db.progressRepository, db.classStudentRepository,
                db.courseRepository, db.assignmentRepository, fileStorageService, ctx, scopeGuard, auditService,
                db.access, db.catalog, db.clock);
    }

    static PortalLessonProgressRequest req(String status) {
        return PortalLessonProgressRequest.builder().status(status).build();
    }

    @Test
    void listClasses_allEnrolled_withPercentAndContinue() {
        db.progress.put(1L, LessonProgressEntity.builder().id(1L).lessonId(10L).studentId(ME).status("COMPLETED").build());
        List<PortalLessonClassDto> list = service.listClasses();
        assertThat(list).extracting("classId").containsExactlyInAnyOrder(CLASS_A, CLASS_C);
        PortalLessonClassDto a = list.stream().filter(x -> x.getClassId() == CLASS_A).findFirst().orElseThrow();
        assertThat(a.getTotalLessons()).isEqualTo(2); // khoá 10 + lớp 11 (12 chưa mở, 13 nháp)
        assertThat(a.getCompletedLessons()).isEqualTo(1);
        assertThat(a.getPercent()).isEqualTo(50);
        assertThat(a.getContinueLessonId()).isEqualTo(11L);
        assertThat(a.getCourseName()).isEqualTo("Khoa TOAN9");
    }

    @Test
    void tree_courseFirst_onlyAvailable() {
        db.videoResource(70L, 11L);
        PortalLessonTreeDto tree = service.getTree(CLASS_A);
        assertThat(tree.getSections()).extracting("source").containsExactly("COURSE", "CLASS");
        assertThat(tree.getSections().get(1).getLessons()).extracting("id").containsExactly(11L);
        assertThat(tree.getSections().get(1).getLessons().get(0).getHasVideo()).isTrue();
        assertThat(tree.getSections().get(1).getLessons().get(0).getProgressStatus()).isEqualTo("NOT_STARTED");
        assertThat(tree.getTotalLessons()).isEqualTo(2);
        assertThat(tree.getServerNow()).isEqualTo(NOW);
    }

    @Test
    void tree_otherClass_isClassNotFound404() {
        assertCode(() -> service.getTree(CLASS_B), "CLASS_NOT_FOUND");
    }

    @Test
    void idor_otherClassLesson_courseOfOtherClass_draft_future_all404() {
        assertCode(() -> service.get(15L, null), "LESSON_NOT_FOUND");      // bài lớp B
        assertCode(() -> service.get(15L, CLASS_B), "LESSON_NOT_FOUND");   // ép classId lớp B
        assertCode(() -> service.get(14L, null), "LESSON_NOT_FOUND");      // khoá gắn lớp B
        assertCode(() -> service.get(13L, CLASS_A), "LESSON_NOT_FOUND");   // nháp
        assertCode(() -> service.get(12L, CLASS_A), "LESSON_NOT_FOUND");   // chưa mở
        assertCode(() -> service.get(10L, CLASS_C), "LESSON_NOT_FOUND");   // bài khoá nhưng lớp C không gắn
        assertCode(() -> service.get(999L, null), "LESSON_NOT_FOUND");
        assertCode(() -> service.recordProgress(15L, null, req("COMPLETED")), "LESSON_NOT_FOUND");
        verify(db.progressRepository, never()).saveAndFlush(any());
    }

    @Test
    void courseLesson_visibleOnlyWhileLinked() {
        assertThat(service.get(10L, null).getClassId()).isEqualTo(CLASS_A);
        db.classes.get(CLASS_A).setCourseId(null); // gỡ khoá
        assertCode(() -> service.get(10L, null), "LESSON_NOT_FOUND");
    }

    @Test
    void detail_prevNext_andDoesNotWriteProgress() {
        PortalLessonDetailDto d = service.get(11L, CLASS_A);
        assertThat(d.getPrevLessonId()).isEqualTo(10L);
        assertThat(d.getNextLessonId()).isNull();
        assertThat(d.getProgress().getStatus()).isEqualTo("NOT_STARTED");
        assertThat(d.getProgress().getClassTotal()).isEqualTo(2);
        verify(db.progressRepository, never()).saveAndFlush(any());
    }

    @Test
    void detail_hidesDraftAssignmentAndOtherClassAssignment() {
        db.assignment(300L, CLASS_A, "PUBLISHED");
        db.assignment(301L, CLASS_A, "DRAFT");
        db.resources.put(80L, com.education.base.entity.LessonResourceEntity.builder().id(80L).lessonId(11L)
                .resourceType("ASSIGNMENT").assignmentId(300L).title("a").sortOrder(0).isDeleted(0).build());
        db.resources.put(81L, com.education.base.entity.LessonResourceEntity.builder().id(81L).lessonId(11L)
                .resourceType("ASSIGNMENT").assignmentId(301L).title("b").sortOrder(1).isDeleted(0).build());
        db.videoResource(82L, 11L);
        PortalLessonDetailDto d = service.get(11L, CLASS_A);
        assertThat(d.getResources()).extracting("id").containsExactly(80L, 82L);
        assertThat(d.getResources().get(1).getUrl()).isNull(); // VIDEO chỉ trả embedUrl
        assertThat(d.getResources().get(1).getEmbedUrl()).startsWith("https://www.youtube-nocookie.com/embed/");
    }

    @Test
    void progress_create_increment_completedNeverRegresses() {
        PortalLessonProgressDto p1 = service.recordProgress(11L, CLASS_A, req("IN_PROGRESS"));
        assertThat(p1.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(p1.getViewCount()).isEqualTo(1);
        assertThat(p1.getFirstViewedAt()).isEqualTo(NOW);
        PortalLessonProgressDto p2 = service.recordProgress(11L, CLASS_A, req("IN_PROGRESS"));
        assertThat(p2.getViewCount()).isEqualTo(2);
        PortalLessonProgressDto p3 = service.recordProgress(11L, CLASS_A, req("COMPLETED"));
        assertThat(p3.getStatus()).isEqualTo("COMPLETED");
        assertThat(p3.getCompletedAt()).isEqualTo(NOW);
        assertThat(p3.getClassCompleted()).isEqualTo(1);
        assertThat(p3.getClassTotal()).isEqualTo(2);
        assertThat(p3.getClassPercent()).isEqualTo(50);
        PortalLessonProgressDto p4 = service.recordProgress(11L, CLASS_A, req("IN_PROGRESS"));
        assertThat(p4.getStatus()).isEqualTo("COMPLETED");
        assertThat(p4.getViewCount()).isEqualTo(3);
        service.recordProgress(11L, CLASS_A, req("COMPLETED"));
        verify(auditService, times(1)).success(eq("LESSON_COMPLETED"), eq("LESSON"), eq(11L), anyMap());
        assertThat(db.progress).hasSize(1);
    }

    @Test
    void progress_completedFirstTime_setsCompletedAt() {
        PortalLessonProgressDto p = service.recordProgress(10L, null, req("COMPLETED"));
        assertThat(p.getStatus()).isEqualTo("COMPLETED");
        assertThat(p.getViewCount()).isEqualTo(1);
        assertThat(p.getCompletedAt()).isEqualTo(NOW);
        assertThat(p.getClassId()).isEqualTo(CLASS_A);
    }

    @Test
    void percent_ignoresUnopenedAndUnpublished() {
        db.progress.put(1L, LessonProgressEntity.builder().id(1L).lessonId(12L).studentId(ME).status("COMPLETED").build());
        db.progress.put(2L, LessonProgressEntity.builder().id(2L).lessonId(13L).studentId(ME).status("COMPLETED").build());
        PortalLessonTreeDto tree = service.getTree(CLASS_A);
        assertThat(tree.getCompletedLessons()).isZero();
        assertThat(tree.getPercent()).isZero();
        a2Future.setAvailableFrom(NOW.minusMinutes(1));
        assertThat(service.getTree(CLASS_A).getPercent()).isEqualTo(33);
    }

    @Test
    void invalidStatus_rejected() {
        assertCode(() -> service.recordProgress(11L, CLASS_A, req("NOT_STARTED")), "VALIDATION_ERROR");
    }
}
