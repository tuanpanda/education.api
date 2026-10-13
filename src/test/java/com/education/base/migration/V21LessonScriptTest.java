package com.education.base.migration;

import com.education.base.entity.CourseEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.LessonProgressEntity;
import com.education.base.entity.LessonResourceEntity;
import com.education.base.entity.LessonSectionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** GĐ3 Luồng 0: quy ước V21_1..V21_4 (học liệu) + khớp entity. */
class V21LessonScriptTest {

    private static final String V21_1 = "/db/migration/V21_1__lms_courses.sql";
    private static final String V21_2 = "/db/migration/V21_2__lms_lessons.sql";
    private static final String V21_3 = "/db/migration/V21_3__lms_lesson_progress.sql";
    private static final String V21_4 = "/db/migration/V21_4__lesson_menus.sql";
    private static final List<String> ALL = List.of(V21_1, V21_2, V21_3, V21_4);
    private static final String GUARD = "WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK";
    private static final Pattern STATEMENT = Pattern.compile(
            "^\\s*(DECLARE|BEGIN|MERGE|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|COMMENT|COMMIT|SELECT)\\b",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Test
    void allScriptsAreAsciiGuardedWithRollbackHeaderAndEndWithCommitExit() throws IOException {
        for (String script : ALL) {
            String text = read(script);
            assertThat(text.chars().filter(ch -> ch > 127).count()).as(script + " ASCII").isZero();
            assertThat(text).as(script).contains("-- ROLLBACK");
            String code = code(text);
            int guard = code.indexOf(GUARD);
            assertThat(guard).as(script).isGreaterThanOrEqualTo(0);
            Matcher first = STATEMENT.matcher(code);
            assertThat(first.find()).isTrue();
            assertThat(first.start()).as(script + ": guard truoc lenh dau").isGreaterThan(guard);
            assertThat(code).as(script + ": pre-check").contains("Kiem tra truoc");
            assertThat(code).as(script + ": post-check").contains("Kiem tra sau");
            List<String> lines = code.lines().map(String::strip).filter(l -> !l.isEmpty())
                    .filter(l -> !l.toUpperCase(Locale.ROOT).startsWith("PROMPT")).toList();
            assertThat(lines.get(lines.size() - 1)).as(script).isEqualToIgnoringCase("EXIT");
            assertThat(lines.get(lines.size() - 2)).as(script).isEqualToIgnoringCase("COMMIT;");
            assertThat(code.toUpperCase(Locale.ROOT)).as(script + ": khong ghi mat khau").doesNotContain("IDENTIFIED BY");
        }
    }

    @Test
    void tablesMatchEntities() throws IOException {
        String v1 = code(read(V21_1)).toUpperCase(Locale.ROOT);
        String v2 = code(read(V21_2)).toUpperCase(Locale.ROOT);
        String v3 = code(read(V21_3)).toUpperCase(Locale.ROOT);
        assertTable(CourseEntity.class, "LMS_COURSES", v1);
        assertTable(LessonSectionEntity.class, "LMS_LESSON_SECTIONS", v2);
        assertTable(LessonEntity.class, "LMS_LESSONS", v2);
        assertTable(LessonResourceEntity.class, "LMS_LESSON_RESOURCES", v2);
        assertTable(LessonProgressEntity.class, "LMS_LESSON_PROGRESS", v3);
        assertThat(v1).contains("SEQ_LMS_COURSES").contains("ALTER TABLE EDU_CLASSES ADD (COURSE_ID NUMBER)")
                .contains("UX_COURSES_CODE");
        assertThat(v2).contains("SEQ_LMS_LESSON_SECTIONS").contains("SEQ_LMS_LESSONS").contains("SEQ_LMS_LESSON_RESOURCES")
                .contains("CK_LSEC_OWNER").contains("CK_LES_OWNER").contains("CK_LRES_SHAPE");
        assertThat(v3).contains("SEQ_LMS_LESSON_PROGRESS").contains("UQ_LPRG_LESSON_STUDENT UNIQUE (LESSON_ID, STUDENT_ID)");
    }

    @Test
    void sequenceNamesMatchEntityGenerators() throws IOException {
        String all = (read(V21_1) + read(V21_2) + read(V21_3)).toUpperCase(Locale.ROOT);
        for (Class<?> type : List.of(CourseEntity.class, LessonSectionEntity.class, LessonEntity.class,
                LessonResourceEntity.class, LessonProgressEntity.class)) {
            String seq = type.getDeclaredFields()[0].getAnnotation(jakarta.persistence.SequenceGenerator.class).sequenceName();
            assertThat(seq).isEqualTo("SEQ_" + type.getAnnotation(Table.class).name());
            assertThat(all).as(type.getSimpleName()).contains("'" + seq + "'");
        }
    }

    @Test
    void v21_4SeedsMenusWithUnistrTeacherLimitsAndLessonModule() throws IOException {
        String text = read(V21_4);
        String upper = code(text).toUpperCase(Locale.ROOT);
        assertThat(text).contains("UNISTR('Kh\\00F3a h\\1ECDc')").contains("UNISTR('H\\1ECDc li\\1EC7u')")
                .contains("'/academic/courses' PATH").contains("'/academic/lessons' PATH");
        assertThat(upper).contains("SEQ_SYS_MENUS.NEXTVAL").contains("SEQ_SYS_FUNCTIONS.NEXTVAL")
                .contains("SEQ_SYS_ROLE_MENU_PERM.NEXTVAL")
                .contains("FUNCTION_CODE IN ('VIEW', 'CREATE', 'UPDATE')")
                .contains("FUNCTION_CODE IN ('VIEW')")
                .contains("'LESSON' MODULE_NAME").contains("WHEN NOT MATCHED THEN INSERT (MODULE_NAME")
                .contains("'PDF,JPG,JPEG,PNG,WEBP,DOC,DOCX,XLS,XLSX,PPTX,TXT'");
    }

    private static void assertTable(Class<?> type, String table, String ddl) {
        assertThat(type.getAnnotation(Table.class).name()).isEqualTo(table);
        int start = ddl.indexOf("CREATE TABLE " + table + " (");
        assertThat(start).as(table).isGreaterThanOrEqualTo(0);
        String body = ddl.substring(start, ddl.indexOf(")]';", start));
        for (String col : columnNames(type)) {
            assertThat(body).as(table + " thieu cot " + col).containsPattern("\\n\\s*" + col + "\\s");
        }
    }

    private static List<String> columnNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null && !column.name().isBlank()) {
                names.add(column.name().toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static String read(String classpath) throws IOException {
        try (InputStream in = V21LessonScriptTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String code(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\\R")) {
            if (line.strip().startsWith("--")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
