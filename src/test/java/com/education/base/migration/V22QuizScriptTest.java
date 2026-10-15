package com.education.base.migration;

import com.education.base.entity.QuestionEntity;
import com.education.base.entity.QuestionOptionEntity;
import com.education.base.entity.QuizAnswerEntity;
import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizAttemptEventEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.entity.QuizQuestionEntity;
import com.education.base.entity.QuizRuleEntity;
import jakarta.persistence.Column;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** GĐ4: quy ước V22_1..V22_4 (trắc nghiệm) + khớp entity. */
class V22QuizScriptTest {

    static final List<String> ALL = List.of("/db/migration/V22_1__lms_question_bank.sql", "/db/migration/V22_2__lms_quizzes.sql",
            "/db/migration/V22_3__lms_quiz_attempts.sql", "/db/migration/V22_4__quiz_menus.sql");
    static final String GUARD = "WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK";
    static final Pattern STATEMENT = Pattern.compile(
            "^\\s*(DECLARE|BEGIN|MERGE|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|COMMENT|COMMIT|SELECT)\\b",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    static final List<Class<?>> ENTITIES = List.of(QuestionEntity.class, QuestionOptionEntity.class, QuizEntity.class,
            QuizQuestionEntity.class, QuizRuleEntity.class, QuizAttemptEntity.class, QuizAnswerEntity.class,
            QuizAttemptEventEntity.class);

    @Test
    void scriptsFollowConventions() throws IOException {
        for (String script : ALL) {
            String text = read(script);
            assertThat(text.chars().filter(ch -> ch > 127).count()).as(script + " ASCII").isZero();
            assertThat(text).as(script).contains("-- ROLLBACK");
            String code = code(text);
            int guard = code.indexOf(GUARD);
            assertThat(guard).as(script).isGreaterThanOrEqualTo(0);
            Matcher first = STATEMENT.matcher(code);
            assertThat(first.find()).isTrue();
            assertThat(first.start()).as(script).isGreaterThan(guard);
            assertThat(code).as(script).contains("Kiem tra truoc").contains("Kiem tra sau");
            List<String> lines = code.lines().map(String::strip).filter(l -> !l.isEmpty())
                    .filter(l -> !l.toUpperCase(Locale.ROOT).startsWith("PROMPT")).toList();
            assertThat(lines.get(lines.size() - 1)).as(script).isEqualToIgnoringCase("EXIT");
            assertThat(lines.get(lines.size() - 2)).as(script).isEqualToIgnoringCase("COMMIT;");
            assertThat(code.toUpperCase(Locale.ROOT)).doesNotContain("IDENTIFIED BY");
        }
    }

    @Test
    void tablesColumnsAndSequencesMatchEntities() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String s : ALL) {
            sb.append(code(read(s))).append('\n');
        }
        String all = sb.toString().toUpperCase(Locale.ROOT);
        for (Class<?> type : ENTITIES) {
            String table = type.getAnnotation(Table.class).name();
            assertThat(all).as(table).contains("CREATE TABLE " + table);
            for (Field f : type.getDeclaredFields()) {
                Column c = f.getAnnotation(Column.class);
                if (c != null) {
                    assertThat(all).as(table + "." + c.name()).contains(c.name().toUpperCase(Locale.ROOT));
                }
                SequenceGenerator g = f.getAnnotation(SequenceGenerator.class);
                if (g != null) {
                    assertThat(g.sequenceName()).isEqualTo("SEQ_" + table);
                    assertThat(all).contains(g.sequenceName());
                }
            }
        }
        assertThat(all).contains("UX_QATT_ONE_IN_PROGRESS").contains("QUIZ_ID").contains("UX_GRADES_QUIZ_STUDENT");
    }

    @Test
    void menusSeedPathsAndActions() throws IOException {
        String upper = code(read(ALL.get(3))).toUpperCase(Locale.ROOT);
        assertThat(upper).contains("/ACADEMIC/QUESTIONS").contains("/ACADEMIC/QUIZZES")
                .contains("MENU_QUESTION_BANK").contains("MENU_QUIZ").contains("'GRADE'").contains("'EXPORT'").contains("'DELETE'");
    }

    @Test
    void candidateSnapshotIsClobWithIsJsonCheck_andEntityIsLob() throws Exception {
        String v2 = code(read(ALL.get(1))).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        assertThat(v2).contains("CANDIDATE_SNAPSHOT CLOB")
                .contains("CONSTRAINT CK_QR_CAND_JSON CHECK (CANDIDATE_SNAPSHOT IS JSON)")
                .contains("DATA_TYPE = 'CLOB'").doesNotContain("CANDIDATE_IDS");
        Field f = QuizRuleEntity.class.getDeclaredField("candidateSnapshot");
        assertThat(f.getAnnotation(Column.class).name()).isEqualTo("CANDIDATE_SNAPSHOT");
        assertThat(f.getAnnotation(jakarta.persistence.Lob.class)).isNotNull();
        for (String s : ALL) {
            assertThat(read(s)).as(s).doesNotContain("CANDIDATE_IDS");
        }
    }

    static String read(String path) throws IOException {
        try (InputStream in = V22QuizScriptTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String code(String text) {
        return text.lines().filter(l -> !l.strip().startsWith("--")).reduce("", (a, b) -> a + b + "\n");
    }
}
