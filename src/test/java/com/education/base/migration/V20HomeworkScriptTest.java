package com.education.base.migration;

import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.entity.SubmissionHistoryEntity;
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

/** GĐ2 Luồng 0: quy ước V20_1..V20_4 (bài tập về nhà) + khớp entity. */
class V20HomeworkScriptTest {

    private static final String V20_1 = "/db/migration/V20_1__lms_assignments.sql";
    private static final String V20_2 = "/db/migration/V20_2__lms_submissions.sql";
    private static final String V20_3 = "/db/migration/V20_3__homework_menus.sql";
    private static final String V20_4 = "/db/migration/V20_4__homework_gradebook.sql";
    private static final List<String> ALL = List.of(V20_1, V20_2, V20_3, V20_4);
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
        String v1 = code(read(V20_1)).toUpperCase(Locale.ROOT);
        String v2 = code(read(V20_2)).toUpperCase(Locale.ROOT);
        assertTable(AssignmentEntity.class, "LMS_ASSIGNMENTS", v1);
        assertTable(SubmissionEntity.class, "LMS_SUBMISSIONS", v2);
        assertTable(SubmissionHistoryEntity.class, "LMS_SUBMISSION_HISTORY", v2);
        assertThat(v1).contains("SEQ_LMS_ASSIGNMENTS").contains("IX_ASG_CLASS_STATUS_DUE");
        assertThat(v2).contains("SEQ_LMS_SUBMISSIONS").contains("SEQ_LMS_SUBMISSION_HISTORY")
                .contains("UQ_SUB_ASG_STUDENT UNIQUE (ASSIGNMENT_ID, STUDENT_ID)")
                .contains("UQ_SUBH_ATTEMPT UNIQUE (SUBMISSION_ID, ATTEMPT_NO)");
    }

    @Test
    void v20_3SeedsMenuWithUnistrAndTeacherWithoutDelete_andEnablesModules() throws IOException {
        String text = read(V20_3);
        String upper = code(text).toUpperCase(Locale.ROOT);
        assertThat(text).contains("UNISTR('B\\00E0i t\\1EADp')").contains("'/academic/homework' PATH");
        assertThat(upper).contains("SEQ_SYS_MENUS.NEXTVAL").contains("SEQ_SYS_FUNCTIONS.NEXTVAL")
                .contains("SEQ_SYS_ROLE_MENU_PERM.NEXTVAL")
                .contains("FUNCTION_CODE IN ('VIEW', 'CREATE', 'UPDATE', 'GRADE', 'EXPORT')")
                .contains("'GRADE'").contains("'EXPORT'");
        assertThat(upper).contains("MODULE_NAME = 'SUBMISSION' AND IS_ACTIVE = 0")
                .contains("'PDF,JPG,JPEG,PNG,WEBP,DOCX,XLSX,TXT'")
                .contains("MAX_FILE_SIZE_BYTES = 10485760, MAX_FILES_PER_REF = 5, MAX_TOTAL_BYTES_PER_REF = 26214400");
    }

    @Test
    void v20_4AddsNullableLinkWithoutChangingGradeTypeCheck() throws IOException {
        String upper = code(read(V20_4)).toUpperCase(Locale.ROOT);
        assertThat(upper).contains("ADD (ASSIGNMENT_ID NUMBER)").contains("UX_GRADES_ASSIGNMENT_STUDENT")
                .doesNotContain("DROP CONSTRAINT CK_GRADES_TYPE");
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
        try (InputStream in = V20HomeworkScriptTest.class.getResourceAsStream(classpath)) {
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
