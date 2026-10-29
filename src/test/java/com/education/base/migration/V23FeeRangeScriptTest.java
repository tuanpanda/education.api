package com.education.base.migration;

import com.education.base.entity.TuitionFeeSessionEntity;
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

/** Quy ước script V23_1..V23_9 (thu học phí theo khoảng ngày - chạy tay bằng sqlplus, không Flyway). */
class V23FeeRangeScriptTest {

    static final String DIR = "/db/migration/";
    static final List<String> ALL = List.of(
            "V23_1__fee_period_columns.sql",
            "V23_2__fee_period_check.sql",
            "V23_3__seq_fee_sessions.sql",
            "V23_4__fee_sessions_table.sql",
            "V23_5__idx_fee_sessions_fee.sql",
            "V23_6__idx_fees_period.sql",
            "V23_7__backfill_legacy_fee_sessions.sql",
            "V23_8__backfill_legacy_fee_period.sql",
            "V23_9__rpt_class_collection_by_session.sql");
    static final List<String> DML = List.of("V23_7__backfill_legacy_fee_sessions.sql",
            "V23_8__backfill_legacy_fee_period.sql");
    static final String GUARD = "WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK";
    static final Pattern STATEMENT = Pattern.compile(
            "^\\s*(DECLARE|BEGIN|MERGE|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|COMMENT|COMMIT|SELECT)\\b",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    static final Pattern CREDENTIAL = Pattern.compile("\\w+/[^\\s/@]+@", Pattern.CASE_INSENSITIVE);

    @Test
    void scriptsExistAsciiAndHaveSessionSettingsBeforeFirstStatement() throws IOException {
        for (String script : ALL) {
            String text = read(script);
            assertThat(text.chars().filter(ch -> ch > 127).count()).as(script + " ASCII").isZero();
            assertThat(text).as(script).contains("-- ROLLBACK").contains("THU TU CHAY")
                    .contains("NLS_LANG=AMERICAN_AMERICA.AL32UTF8").contains("/nolog")
                    .contains("(mat khau nhap tai prompt)");
            String code = code(text);
            int guard = code.indexOf(GUARD);
            int define = code.indexOf("SET DEFINE OFF");
            int blank = code.indexOf("SET SQLBLANKLINES ON");
            assertThat(guard).as(script).isGreaterThanOrEqualTo(0);
            assertThat(define).as(script).isGreaterThanOrEqualTo(0);
            assertThat(blank).as(script).isGreaterThanOrEqualTo(0);
            Matcher first = STATEMENT.matcher(code);
            assertThat(first.find()).as(script).isTrue();
            assertThat(first.start()).as(script).isGreaterThan(Math.max(guard, Math.max(define, blank)));
        }
    }

    @Test
    void oneStatementBlockPerFile() throws IOException {
        for (String script : ALL) {
            String code = code(read(script));
            Matcher m = STATEMENT.matcher(stripBlockBodies(code));
            int count = 0;
            while (m.find()) {
                count++;
            }
            assertThat(count).as(script + " số khối lệnh").isEqualTo(1);
        }
    }

    @Test
    void noTrailingCommentAfterSemicolon_noCredentials() throws IOException {
        for (String script : ALL) {
            String text = read(script);
            assertThat(Pattern.compile(";[ \\t]*--").matcher(text).find()).as(script + " comment sau ;").isFalse();
            String upper = text.toUpperCase(Locale.ROOT);
            assertThat(upper).as(script).doesNotContain("IDENTIFIED BY").doesNotContain("PASSWORD")
                    .doesNotContain(" SYS ").doesNotContain("AS SYSDBA");
            assertThat(CREDENTIAL.matcher(text).find()).as(script + " user/pass@").isFalse();
        }
    }

    @Test
    void dmlScriptsNeitherCommitNorExit() throws IOException {
        for (String script : DML) {
            String upper = read(script).toUpperCase(Locale.ROOT);
            assertThat(upper).as(script).doesNotContain("COMMIT");
            assertThat(Pattern.compile("^\\s*EXIT\\b", Pattern.MULTILINE).matcher(upper).find()).as(script).isFalse();
        }
        assertThat(read("V23_7__backfill_legacy_fee_sessions.sql")).contains("NOT EXISTS").contains("'V23_7_BACKFILL'");
        assertThat(read("V23_8__backfill_legacy_fee_period.sql")).contains("PERIOD_FROM IS NULL");
    }

    @Test
    void ddlScriptsAreIdempotent() throws IOException {
        assertThat(read("V23_1__fee_period_columns.sql")).contains("-1430").contains("already exists");
        assertThat(read("V23_2__fee_period_check.sql")).contains("-2264").contains("already exists");
        assertThat(read("V23_3__seq_fee_sessions.sql")).contains("-955").contains("already exists");
        assertThat(read("V23_4__fee_sessions_table.sql")).contains("-955").contains("-2261").contains("-2264")
                .contains("-2275").contains("already exists");
        assertThat(read("V23_5__idx_fee_sessions_fee.sql")).contains("-955").contains("already exists");
        assertThat(read("V23_6__idx_fees_period.sql")).contains("-955").contains("already exists");
        assertThat(read("V23_9__rpt_class_collection_by_session.sql")).contains("CREATE OR REPLACE PROCEDURE")
                .contains("C_SPLIT_BY_SESSION CONSTANT NUMBER := 1;");
    }

    @Test
    void sessionTableMatchesEntity() throws IOException {
        String table = TuitionFeeSessionEntity.class.getAnnotation(Table.class).name();
        String ddl = code(read("V23_4__fee_sessions_table.sql")).toUpperCase(Locale.ROOT);
        assertThat(ddl).contains("CREATE TABLE " + table).contains("UQ_FEE_SESSIONS_ATTENDANCE UNIQUE (ATTENDANCE_ID)");
        for (Field f : TuitionFeeSessionEntity.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) {
                assertThat(ddl).as(table + "." + c.name()).contains(c.name().toUpperCase(Locale.ROOT));
            }
            SequenceGenerator g = f.getAnnotation(SequenceGenerator.class);
            if (g != null) {
                assertThat(g.sequenceName()).isEqualTo("SEQ_" + table);
                assertThat(read("V23_3__seq_fee_sessions.sql")).contains(g.sequenceName());
            }
        }
        String period = read("V23_1__fee_period_columns.sql");
        assertThat(period).contains("PERIOD_FROM DATE").contains("PERIOD_TO DATE")
                .contains("IS_RANGE NUMBER(1) DEFAULT 0 NOT NULL")
                .contains("CONSTRAINT CK_FEES_IS_RANGE CHECK (IS_RANGE IN (0, 1))")
                .contains("DROP (PERIOD_FROM, PERIOD_TO, IS_RANGE)");
        for (Field f : com.education.base.entity.TuitionFeeEntity.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null && (c.name().startsWith("PERIOD_") || c.name().equals("IS_RANGE"))) {
                assertThat(period).as("V23_1 " + c.name()).contains(c.name());
            }
        }
        assertThat(read("V23_9__rpt_class_collection_by_session.sql")).contains("f.IS_RANGE = 1");
    }

    static String read(String name) throws IOException {
        try (InputStream in = V23FeeRangeScriptTest.class.getResourceAsStream(DIR + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Bỏ dòng comment {@code --}. */
    static String code(String text) {
        StringBuilder sb = new StringBuilder();
        text.lines().filter(l -> !l.strip().startsWith("--")).forEach(l -> sb.append(l).append('\n'));
        return sb.toString();
    }

    /**
     * Thân khối PL/SQL ({@code BEGIN..END;/}, {@code CREATE ... /}) và câu SQL nhiều dòng được tính là 1 lệnh:
     * chỉ giữ dòng đầu mỗi lệnh (kết thúc bằng {@code ;} ở mức SQL hoặc {@code /} một mình trên dòng).
     */
    static String stripBlockBodies(String code) {
        StringBuilder out = new StringBuilder();
        boolean inStatement = false;
        boolean plsql = false;
        for (String line : code.split("\n")) {
            String s = line.strip();
            if (!inStatement) {
                if (STATEMENT.matcher(line).find()) {
                    out.append(line).append('\n');
                    String up = s.toUpperCase(Locale.ROOT);
                    plsql = up.startsWith("BEGIN") || up.startsWith("DECLARE") || up.startsWith("CREATE OR REPLACE");
                    inStatement = !(!plsql && s.endsWith(";"));
                }
                continue;
            }
            if (plsql ? s.equals("/") : s.endsWith(";")) {
                inStatement = false;
            }
        }
        return out.toString();
    }
}
