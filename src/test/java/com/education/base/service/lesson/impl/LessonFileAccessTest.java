package com.education.base.service.lesson.impl;

import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonSectionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.education.base.service.lesson.impl.LessonFakeDb.NOW;
import static org.assertj.core.api.Assertions.assertThat;

/** File LESSON dùng chung giữa 2 bài (khoá mẫu + bản chép ở lớp): quyền học sinh theo mọi tài nguyên dùng file. */
class LessonFileAccessTest {

    LessonFakeDb db = new LessonFakeDb();
    LessonFileAccess access = new LessonFileAccess(db.resourceRepository, db.lessonRepository, db.classStudentRepository,
            db.catalog, db.clock);
    LessonEntity courseLesson;
    LessonEntity copyInB;

    @BeforeEach
    void setUp() {
        db.course(100L, "TOAN9", "ACTIVE");
        db.clazz(7L, 5L, 100L);   // lớp A gắn khoá
        db.clazz(8L, 5L, null);   // lớp B có bản chép
        db.clazz(9L, 5L, null);   // lớp C không liên quan
        LessonSectionEntity sc = db.section(1L, 100L, null, 0);
        LessonSectionEntity sb = db.section(2L, null, 8L, 0);
        courseLesson = db.lesson(10L, sc, 0);
        copyInB = db.lesson(11L, sb, 0);
        copyInB.setSourceLessonId(10L);
        db.fileResource(70L, 10L, 900L);
        db.fileResource(71L, 11L, 900L);
        db.enroll(1L, 7L, 42L);
        db.enroll(2L, 8L, 43L);
        db.enroll(3L, 9L, 44L);
    }

    @Test
    void sharedFile_readableFromEitherLesson() {
        assertThat(access.studentCanRead(42L, 900L)).isTrue();  // qua khoá gắn lớp A
        assertThat(access.studentCanRead(43L, 900L)).isTrue();  // qua bản chép lớp B
        assertThat(access.studentCanRead(44L, 900L)).isFalse(); // lớp C
        assertThat(access.lessonsUsingFile(900L)).extracting("id").containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void copyDraft_orResourceRemoved_orUnopened_blocks() {
        copyInB.setStatus("DRAFT");
        assertThat(access.studentCanRead(43L, 900L)).isFalse();
        assertThat(access.studentCanRead(42L, 900L)).isTrue();
        courseLesson.setAvailableFrom(NOW.plusDays(1));
        assertThat(access.studentCanRead(42L, 900L)).isFalse();
        courseLesson.setAvailableFrom(null);
        db.resources.get(70L).setIsDeleted(1);
        assertThat(access.studentCanRead(42L, 900L)).isFalse();
        assertThat(access.studentCanRead(42L, 901L)).isFalse();
        assertThat(access.studentCanRead(null, 900L)).isFalse();
    }

    @Test
    void unlinkedCourse_blocks() {
        db.classes.get(7L).setCourseId(null);
        assertThat(access.studentCanRead(42L, 900L)).isFalse();
    }
}
