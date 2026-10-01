package com.education.base.migration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quy ước chung cho các script V14_x (tài chính, mỗi stream sở hữu một file):
 * <ul>
 *     <li>chạy bằng sqlplus: {@code WHENEVER SQLERROR EXIT ... ROLLBACK} trước lệnh đầu tiên, kết thúc bằng
 *         {@code COMMIT} rồi {@code EXIT};</li>
 *     <li>menu chỉ được seed bằng {@code MERGE INTO SYS_MENUS ... ON (t.MENU_CODE = s.MENU_CODE)} với
 *         {@code SEQ_SYS_MENUS.NEXTVAL} — không dùng ID cố định (tránh trùng khóa giữa các stream);</li>
 *     <li>không {@code INSERT INTO SYS_MENUS} trực tiếp.</li>
 * </ul>
 * Dòng comment ({@code --}) bị bỏ qua, nên các mẫu MERGE đang comment trong stub không bị kiểm tra.
 */
class V14ScriptConventionTest {

    private static final Pattern STATEMENT_START = Pattern.compile(
            "^(DECLARE|BEGIN|MERGE|INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|COMMIT)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MERGE_MENUS = Pattern.compile(
            "MERGE\\s+INTO\\s+SYS_MENUS\\b(.*?);", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @ParameterizedTest
    @ValueSource(strings = {
            "/db/migration/V14_1__fin_billing.sql",
            "/db/migration/V14_2__fin_payments.sql",
            "/db/migration/V14_3__fin_reports.sql"
    })
    void followsSharedConventions(String script) throws IOException {
        String sql = withoutComments(read(script));

        int whenever = sql.indexOf("WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK");
        assertThat(whenever).as("WHENEVER SQLERROR trong %s", script).isGreaterThanOrEqualTo(0);
        assertThat(whenever).as("WHENEVER SQLERROR phải đứng trước lệnh đầu tiên (%s)", script)
                .isLessThan(firstStatementOffset(sql));

        List<String> lines = nonBlankLines(sql);
        assertThat(lines).as("%s kết thúc bằng COMMIT; ... EXIT", script).isNotEmpty();
        assertThat(lines.get(lines.size() - 1)).isEqualToIgnoringCase("EXIT");
        assertThat(lines).anyMatch(line -> line.equalsIgnoreCase("COMMIT;"));

        String upper = sql.toUpperCase(Locale.ROOT);
        assertThat(upper).as("không INSERT INTO SYS_MENUS trực tiếp (%s)", script)
                .doesNotContainPattern("INSERT\\s+INTO\\s+SYS_MENUS\\b");

        Matcher merge = MERGE_MENUS.matcher(sql);
        while (merge.find()) {
            String block = merge.group(1).toUpperCase(Locale.ROOT);
            assertThat(block).as("MERGE SYS_MENUS khớp theo MENU_CODE (%s)", script)
                    .containsPattern("ON\\s*\\(\\s*T\\.MENU_CODE\\s*=\\s*S\\.MENU_CODE\\s*\\)");
            assertThat(block).as("MERGE SYS_MENUS lấy ID từ SEQ_SYS_MENUS.NEXTVAL (%s)", script)
                    .contains("SEQ_SYS_MENUS.NEXTVAL");
        }
    }

    private String read(String script) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(script)) {
            assertThat(in).as("không tìm thấy %s", script).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static String withoutComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        for (String line : sql.split("\n", -1)) {
            out.append(line.stripLeading().startsWith("--") ? "" : line).append('\n');
        }
        return out.toString();
    }

    private static int firstStatementOffset(String sql) {
        int offset = 0;
        for (String line : sql.split("\n", -1)) {
            if (STATEMENT_START.matcher(line.strip()).find()) {
                return offset;
            }
            offset += line.length() + 1;
        }
        return Integer.MAX_VALUE;
    }

    private static List<String> nonBlankLines(String sql) {
        List<String> lines = new ArrayList<>();
        for (String line : sql.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.strip());
            }
        }
        return lines;
    }
}
