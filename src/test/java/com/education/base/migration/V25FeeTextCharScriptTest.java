package com.education.base.migration;

import com.education.base.common.DomainConstants;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.TuitionFeeEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.education.base.migration.V23FeeRangeScriptTest.CREDENTIAL;
import static com.education.base.migration.V23FeeRangeScriptTest.GUARD;
import static com.education.base.migration.V23FeeRangeScriptTest.STATEMENT;
import static com.education.base.migration.V23FeeRangeScriptTest.code;
import static com.education.base.migration.V23FeeRangeScriptTest.read;
import static com.education.base.migration.V23FeeRangeScriptTest.reservedIdentifiers;
import static com.education.base.migration.V23FeeRangeScriptTest.stripBlockBodies;
import static org.assertj.core.api.Assertions.assertThat;

/** Quy ước script V25_1 (cột văn bản phí sang CHAR, BUG-FEE-RANGE-04 - chạy tay bằng sqlplus). */
class V25FeeTextCharScriptTest {

    static final String SCRIPT = "V25_1__fee_text_columns_char.sql";

    /** Cột -> độ dài ký tự mong đợi (bằng {@code @Column(length)} của entity). */
    static Map<String, Integer> expected() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("FIN_TUITION_FEES.NOTE", 255);
        m.put("FIN_TUITION_FEES.CANCEL_REASON", 255);
        m.put("FIN_TUITION_FEES.TEACHER_COMMENT", DomainConstants.FEE_TEACHER_COMMENT_MAX);
        m.put("FIN_TUITION_FEES.FOOTER_WISH", DomainConstants.FEE_FOOTER_WISH_MAX);
        m.put("FIN_TUITION_FEES.SLIP_LABEL", 50);
        m.put("FIN_PAYMENT_TRANSACTIONS.NOTE", 255);
        m.put("FIN_PAYMENT_TRANSACTIONS.PAYER_NAME", 150);
        m.put("FIN_PAYMENT_TRANSACTIONS.VOID_REASON", 255);
        return m;
    }

    @Test
    void asciiHeaderAndSessionSettingsBeforeFirstStatement() throws IOException {
        String text = read(SCRIPT);
        assertThat(text.chars().filter(ch -> ch > 127).count()).as("ASCII").isZero();
        assertThat(text).contains("-- ROLLBACK").contains("THU TU CHAY").contains("-- KIEM TRA")
                .contains("NLS_LANG=AMERICAN_AMERICA.AL32UTF8").contains("/nolog")
                .contains("CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)")
                .contains("docs/RUN-V25-fee-text-char.md");
        String code = code(text);
        int guard = code.indexOf(GUARD);
        int define = code.indexOf("SET DEFINE OFF");
        int blank = code.indexOf("SET SQLBLANKLINES ON");
        assertThat(guard).isGreaterThanOrEqualTo(0);
        assertThat(define).isGreaterThanOrEqualTo(0);
        assertThat(blank).isGreaterThanOrEqualTo(0);
        Matcher first = STATEMENT.matcher(code);
        assertThat(first.find()).isTrue();
        assertThat(first.start()).isGreaterThan(Math.max(guard, Math.max(define, blank)));
    }

    @Test
    void oneBlock_noTrailingComment_noCredentials_noCommitNoExit_ddlOnly() throws IOException {
        String text = read(SCRIPT);
        Matcher m = STATEMENT.matcher(stripBlockBodies(code(text)));
        int count = 0;
        while (m.find()) {
            count++;
        }
        assertThat(count).as("số khối lệnh").isEqualTo(1);
        assertThat(Pattern.compile(";[ \\t]*--").matcher(text).find()).as("comment sau ;").isFalse();
        String upper = text.toUpperCase(Locale.ROOT);
        assertThat(upper).doesNotContain("IDENTIFIED BY").doesNotContain("PASSWORD")
                .doesNotContain(" SYS ").doesNotContain("AS SYSDBA");
        assertThat(CREDENTIAL.matcher(text).find()).as("user/pass@").isFalse();
        String body = code(text).toUpperCase(Locale.ROOT);
        assertThat(Pattern.compile("\\bCOMMIT\\b").matcher(body).find()).as("COMMIT").isFalse();
        assertThat(Pattern.compile("^\\s*EXIT\\b", Pattern.MULTILINE).matcher(body).find()).isFalse();
        assertThat(body).doesNotContain("EXITCOMMIT");
        assertThat(Pattern.compile("\\b(INSERT|UPDATE|DELETE|MERGE)\\b").matcher(body).find())
                .as("không ghi dữ liệu").isFalse();
        assertThat(body).doesNotContain("DROP");
    }

    @Test
    void idempotent_onlyByteColumnsModified_neverShrinks() throws IOException {
        String body = code(read(SCRIPT));
        assertThat(body).contains("IF V_USED = 'C' THEN").contains("already CHAR")
                .contains("GREATEST(V_CHAR_LEN, P_LEN)")
                .contains("' MODIFY (' || P_COL").contains("' CHAR))'")
                .contains("FROM USER_TAB_COLUMNS").contains("not found");
    }

    @Test
    void columnListMatchesEntityLengths_partialReasonVerifiedOnly() throws IOException {
        String body = code(read(SCRIPT));
        Map<String, Integer> exp = expected();
        Matcher m = Pattern.compile("TO_CHAR_SEMANTICS\\('(\\w+)', '(\\w+)', (\\d+)\\);").matcher(body);
        Map<String, Integer> actual = new LinkedHashMap<>();
        while (m.find()) {
            actual.put(m.group(1) + "." + m.group(2), Integer.parseInt(m.group(3)));
        }
        assertThat(actual).isEqualTo(exp);
        assertThat(body).contains("VERIFY_CHAR('FIN_TUITION_FEES', 'PARTIAL_REASON', "
                + DomainConstants.FEE_PARTIAL_REASON_MAX + ");");
        assertThat(body.indexOf("VERIFY_CHAR('FIN_TUITION_FEES'"))
                .as("kiểm PARTIAL_REASON trước khi sửa cột nào")
                .isLessThan(body.indexOf("TO_CHAR_SEMANTICS('FIN_TUITION_FEES'"));
        assertThat(body).doesNotContain("TO_CHAR_SEMANTICS('FIN_TUITION_FEES', 'PARTIAL_REASON'");

        assertEntityLength(TuitionFeeEntity.class, "FIN_TUITION_FEES", exp);
        assertEntityLength(PaymentTransactionEntity.class, "FIN_PAYMENT_TRANSACTIONS", exp);
        String header = read(SCRIPT);
        exp.forEach((col, len) -> assertThat(header).as("rollback " + col)
                .contains(col.substring(col.indexOf('.') + 1) + " VARCHAR2(" + len + " BYTE)"));
    }

    private static void assertEntityLength(Class<?> entity, String table, Map<String, Integer> exp) {
        int found = 0;
        for (var f : entity.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null && exp.containsKey(table + "." + c.name())) {
                assertThat(c.length()).as(table + "." + c.name()).isEqualTo(exp.get(table + "." + c.name()));
                found++;
            }
        }
        assertThat(found).as(table).isEqualTo((int) exp.keySet().stream().filter(k -> k.startsWith(table + ".")).count());
    }

    @Test
    void noOracleReservedWordUsedAsAliasOrIdentifier() throws IOException {
        assertThat(reservedIdentifiers(read(SCRIPT))).isEmpty();
    }
}
