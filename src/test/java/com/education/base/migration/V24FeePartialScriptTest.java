package com.education.base.migration;

import com.education.base.common.DomainConstants;
import com.education.base.entity.TuitionFeeEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
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

/** Quy ước script V24_1..V24_2 (phiếu gói chưa đủ 10 buổi, contract v1.4 §13.2 - chạy tay bằng sqlplus). */
class V24FeePartialScriptTest {

    static final List<String> ALL = List.of(
            "V24_1__fee_partial_columns.sql",
            "V24_2__fee_partial_reason_check.sql");

    @Test
    void scriptsAsciiHeaderAndSessionSettingsBeforeFirstStatement() throws IOException {
        for (String script : ALL) {
            String text = read(script);
            assertThat(text.chars().filter(ch -> ch > 127).count()).as(script + " ASCII").isZero();
            assertThat(text).as(script).contains("-- ROLLBACK").contains("THU TU CHAY").contains("-- KIEM TRA")
                    .contains("NLS_LANG=AMERICAN_AMERICA.AL32UTF8").contains("/nolog")
                    .contains("CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)")
                    .contains("docs/RUN-V24-fee-partial.md");
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
    void oneBlock_noTrailingComment_noCredentials_noCommitNoExit_ddlOnly() throws IOException {
        for (String script : ALL) {
            String text = read(script);
            Matcher m = STATEMENT.matcher(stripBlockBodies(code(text)));
            int count = 0;
            while (m.find()) {
                count++;
            }
            assertThat(count).as(script + " số khối lệnh").isEqualTo(1);
            assertThat(Pattern.compile(";[ \\t]*--").matcher(text).find()).as(script + " comment sau ;").isFalse();
            String upper = text.toUpperCase(Locale.ROOT);
            assertThat(upper).as(script).doesNotContain("IDENTIFIED BY").doesNotContain("PASSWORD")
                    .doesNotContain(" SYS ").doesNotContain("AS SYSDBA");
            assertThat(CREDENTIAL.matcher(text).find()).as(script + " user/pass@").isFalse();
            String body = code(text).toUpperCase(Locale.ROOT);
            assertThat(Pattern.compile("\\bCOMMIT\\b").matcher(body).find()).as(script + " COMMIT").isFalse();
            assertThat(Pattern.compile("^\\s*EXIT\\b", Pattern.MULTILINE).matcher(body).find()).isFalse();
            // Như V23: EXITCOMMIT chỉ dùng cho file DML; V24 chỉ DDL.
            assertThat(body).as(script).doesNotContain("EXITCOMMIT");
            assertThat(Pattern.compile("\\b(INSERT|UPDATE|DELETE|MERGE)\\b").matcher(body).find())
                    .as(script + " không ghi dữ liệu").isFalse();
        }
    }

    @Test
    void idempotent_alreadyExistsCodes() throws IOException {
        assertThat(read("V24_1__fee_partial_columns.sql")).contains("-1430").contains("-2264")
                .contains("IS_PARTIAL: already exists").contains("PARTIAL_REASON: already exists");
        assertThat(read("V24_2__fee_partial_reason_check.sql")).contains("-2264")
                .contains("CK_FEES_PARTIAL_REASON: already exists");
    }

    @Test
    void columnsMatchEntityAndConstants() throws IOException {
        String v1 = read("V24_1__fee_partial_columns.sql");
        assertThat(v1).contains("IS_PARTIAL NUMBER(1) DEFAULT 0 NOT NULL")
                .contains("CONSTRAINT CK_FEES_IS_PARTIAL CHECK (IS_PARTIAL IN (0, 1))")
                .contains("PARTIAL_REASON VARCHAR2(" + DomainConstants.FEE_PARTIAL_REASON_MAX + " CHAR)")
                .contains("DROP (IS_PARTIAL, PARTIAL_REASON)");
        int found = 0;
        for (var f : TuitionFeeEntity.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null && c.name().contains("PARTIAL")) {
                assertThat(v1).as("V24_1 " + c.name()).contains(c.name());
                if (c.name().equals("PARTIAL_REASON")) {
                    assertThat(c.length()).isEqualTo(DomainConstants.FEE_PARTIAL_REASON_MAX);
                }
                found++;
            }
        }
        assertThat(found).isEqualTo(2);
        assertThat(read("V24_2__fee_partial_reason_check.sql"))
                .contains("(IS_PARTIAL = 0 AND PARTIAL_REASON IS NULL)")
                .contains("(IS_PARTIAL = 1 AND IS_RANGE = 1 AND PARTIAL_REASON IS NOT NULL)")
                .contains("DROP CONSTRAINT CK_FEES_PARTIAL_REASON");
    }

    @Test
    void noOracleReservedWordUsedAsAliasOrIdentifier() throws IOException {
        for (String script : ALL) {
            assertThat(reservedIdentifiers(read(script))).as(script).isEmpty();
        }
    }
}
