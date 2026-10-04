package com.education.base.migration;

import com.education.base.entity.AuditLogEntity;
import com.education.base.security.Permissions;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V17_3 (Giai đoạn 0 - stream B): bảng {@code SYS_AUDIT_LOGS} + menu {@code MENU_AUDIT_LOG}.
 * Quy ước sqlplus (WHENEVER ... ROLLBACK, COMMIT + EXIT), chạy lại an toàn, seed bằng MERGE, UTF-8 không BOM,
 * và cột trong script khớp {@link AuditLogEntity}.
 */
class V17_3AuditLogScriptTest {

    private static final String SCRIPT = "/db/migration/V17_3__audit_log.sql";

    private static final List<String> COLUMNS = List.of("ID", "EVENT_TIME", "USER_ID", "USERNAME", "USER_TYPE",
            "ACTION", "RESOURCE_TYPE", "RESOURCE_ID", "IP", "USER_AGENT", "RESULT", "DETAIL");

    private static final Pattern STATEMENT_START = Pattern.compile(
            "^(DECLARE|BEGIN|MERGE|INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|COMMENT|COMMIT|SELECT)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    void isUtf8WithoutBomAndCarriesVietnameseMenuName() throws IOException {
        byte[] bytes = bytes();
        assertThat(bytes.length).isGreaterThan(3);
        assertThat(bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF)
                .as("không có BOM (sqlplus đọc BOM như ký tự rác)").isFalse();
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException ex) {
            throw new AssertionError("V17_3 phải là UTF-8 hợp lệ", ex);
        }
        String sql = read();
        assertThat(sql).contains("'Nhật ký hệ thống' MENU_NAME")
                .contains("NLS_LANG=AMERICAN_AMERICA.AL32UTF8");
        // Kiểm tra sau so sánh với UNISTR: phát hiện chạy sai bảng mã.
        assertThat(sql).contains("UNISTR('Nh\\1EADt k\\00FD h\\1EC7 th\\1ED1ng')");
        assertThat(decodeUnistr("Nh\\1EADt k\\00FD h\\1EC7 th\\1ED1ng")).isEqualTo("Nhật ký hệ thống");
    }

    @Test
    void followsSqlplusConventions() throws IOException {
        String code = code(read());
        int guard = code.indexOf("WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK");
        assertThat(guard).isGreaterThanOrEqualTo(0);
        assertThat(guard).as("WHENEVER SQLERROR đứng trước lệnh đầu tiên").isLessThan(firstStatementOffset(code));
        assertThat(code).contains("SET DEFINE OFF");

        List<String> lines = code.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        assertThat(lines.get(lines.size() - 1)).isEqualToIgnoringCase("EXIT");
        assertThat(lines).anyMatch(line -> line.equalsIgnoreCase("COMMIT;"));
        assertThat(code.lastIndexOf("COMMIT;")).isGreaterThan(code.lastIndexOf("MERGE INTO"));
    }

    @Test
    void ddlIsIdempotentAndNeverDropsOutsideRollbackNotes() throws IOException {
        String code = code(read());
        String upper = code.toUpperCase(Locale.ROOT);
        assertThat(upper).doesNotContainPattern("\\bDROP\\s+(TABLE|SEQUENCE|INDEX)\\b")
                .doesNotContainPattern("\\bTRUNCATE\\b")
                .doesNotContainPattern("\\bDELETE\\s+FROM\\b");
        // Mọi CREATE nằm trong khối DDL(...) bắt lỗi "đã tồn tại".
        assertThat(upper).doesNotContainPattern("(?m)^\\s*CREATE\\s+");
        assertThat(code).contains("IF SQLCODE IN (-955, -1408, -2260, -2261, -2264, -2275) THEN");
        assertThat(code).contains("'CREATE SEQUENCE SEQ_SYS_AUDIT_LOGS")
                .contains("CREATE TABLE SYS_AUDIT_LOGS (")
                .contains("CONSTRAINT PK_SYS_AUDIT_LOGS PRIMARY KEY (ID)")
                .contains("CHECK (RESULT IN ('SUCCESS', 'FAILURE', 'DENIED'))");
        assertThat(code).contains("ON SYS_AUDIT_LOGS (EVENT_TIME)")
                .contains("ON SYS_AUDIT_LOGS (USER_ID, EVENT_TIME)")
                .contains("ON SYS_AUDIT_LOGS (ACTION, EVENT_TIME)")
                .contains("ON SYS_AUDIT_LOGS (RESOURCE_TYPE, RESOURCE_ID)");
        // USER_TYPE: chuỗi tự do, không CHECK / FK (không phụ thuộc V17_1 của stream A); USER_ID không FK.
        assertThat(upper).doesNotContain("REFERENCES SYS_USERS").doesNotContainPattern("CHECK\\s*\\(\\s*USER_TYPE");
    }

    @Test
    void tableDeclaresExactlyTheExpectedColumns() throws IOException {
        String table = createTableBody(read());
        Set<String> declared = new LinkedHashSet<>();
        for (String line : table.lines().map(String::strip).toList()) {
            Matcher matcher = Pattern.compile("^([A-Z_]+)\\s+(NUMBER|VARCHAR2|TIMESTAMP)").matcher(line);
            if (matcher.find()) {
                declared.add(matcher.group(1));
            }
        }
        assertThat(declared).containsExactlyElementsOf(COLUMNS);
        assertThat(table).containsPattern("EVENT_TIME\\s+TIMESTAMP\\(6\\)\\s+DEFAULT SYSTIMESTAMP NOT NULL")
                .containsPattern("USER_ID\\s+NUMBER\\(19\\),")
                .containsPattern("USER_TYPE\\s+VARCHAR2\\(20\\),")
                .containsPattern("ACTION\\s+VARCHAR2\\(50\\)\\s+NOT NULL")
                .containsPattern("DETAIL\\s+VARCHAR2\\(4000\\)");
    }

    @Test
    void entityMapsTheSameColumnsAndLengths() throws IOException {
        assertThat(AuditLogEntity.class.getAnnotation(Table.class).name()).isEqualTo("SYS_AUDIT_LOGS");
        List<String> mapped = new ArrayList<>();
        String table = createTableBody(read());
        for (Field field : AuditLogEntity.class.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column == null) {
                continue;
            }
            mapped.add(column.name());
            Matcher varchar = Pattern.compile("\\b" + column.name() + "\\s+VARCHAR2\\((\\d+)\\)").matcher(table);
            if (varchar.find()) {
                assertThat(column.length()).as("độ dài %s", column.name())
                        .isEqualTo(Integer.parseInt(varchar.group(1)));
            }
        }
        assertThat(mapped).containsExactlyInAnyOrderElementsOf(COLUMNS);
    }

    @Test
    void seedsAuditMenuUnderSystemWithViewForAdmin() throws IOException {
        String code = code(read());
        String[] permission = Permissions.AUDIT_LOG_VIEW.split(Permissions.SEPARATOR);
        assertThat(permission).containsExactly("MENU_AUDIT_LOG", "VIEW");

        Matcher menus = Pattern.compile("MERGE\\s+INTO\\s+SYS_MENUS\\b(.*?);", Pattern.DOTALL).matcher(code);
        assertThat(menus.find()).isTrue();
        String menu = menus.group(1);
        assertThat(menu).contains("'MENU_AUDIT_LOG' MENU_CODE")
                .contains("'/system/audit-logs' PATH")
                // Sau MENU_STUDENT_ACCOUNT (V17_2 của stream A, SORT_ORDER 5) dưới DIR_SYSTEM.
                .contains("'shield' ICON, 6 SORT_ORDER")
                .contains("JOIN SYS_MENUS p ON p.MENU_CODE = 'DIR_SYSTEM'")
                .containsPattern("ON\\s*\\(\\s*t\\.MENU_CODE\\s*=\\s*s\\.MENU_CODE\\s*\\)")
                .contains("SEQ_SYS_MENUS.NEXTVAL");
        assertThat(code.toUpperCase(Locale.ROOT)).doesNotContainPattern("INSERT\\s+INTO\\s+SYS_MENUS\\b");

        assertThat(code).contains("'VIEW' AS FUNCTION_CODE").contains("SEQ_SYS_FUNCTIONS.NEXTVAL");
        assertThat(code).contains("WHERE r.ROLE_CODE = 'ROLE_ADMIN'").contains("SEQ_SYS_ROLE_MENU_PERM.NEXTVAL");
        // Chỉ nối VIEW khi chưa có (không ghi đè quyền admin đã chỉnh).
        assertThat(code).containsPattern("WHERE INSTR\\([^;]*ALLOWED_FUNCTIONS[^;]*\\) = 0");
        assertThat(dmlTargets(code)).containsExactly("SYS_MENUS", "SYS_FUNCTIONS", "SYS_ROLE_MENU_PERMISSIONS");
    }

    @Test
    void headerDocumentsRunCommandAndRollback() throws IOException {
        String header = read().lines().takeWhile(line -> line.startsWith("--")).collect(Collectors.joining("\n"));
        assertThat(header).contains("sqlplus.exe EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V17_3__audit_log.sql")
                .contains("ROLLBACK")
                .contains("DROP TABLE SYS_AUDIT_LOGS PURGE;")
                .contains("DROP SEQUENCE SEQ_SYS_AUDIT_LOGS;")
                .contains("DELETE FROM SYS_MENUS WHERE MENU_CODE = 'MENU_AUDIT_LOG';");
    }

    @Test
    void neverStoresSecretsColumns() throws IOException {
        String table = createTableBody(read()).toUpperCase(Locale.ROOT);
        assertThat(table).doesNotContain("PASSWORD").doesNotContain("TOKEN").doesNotContain("COOKIE");
    }

    // ---------------------------------------------------------------------------------------------

    private static String createTableBody(String sql) {
        int start = sql.indexOf("CREATE TABLE SYS_AUDIT_LOGS (");
        assertThat(start).isGreaterThan(0);
        int end = sql.indexOf(")]');", start);
        assertThat(end).isGreaterThan(start);
        return sql.substring(start, end);
    }

    private static List<String> dmlTargets(String code) {
        Matcher matcher = Pattern.compile("\\b(?:INSERT\\s+INTO|DELETE\\s+FROM|MERGE\\s+INTO|UPDATE)\\s+(SYS_\\w+)")
                .matcher(code.toUpperCase(Locale.ROOT));
        List<String> targets = new ArrayList<>();
        while (matcher.find()) {
            targets.add(matcher.group(1));
        }
        return targets;
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

    private static String decodeUnistr(String value) {
        Matcher matcher = Pattern.compile("\\\\([0-9A-Fa-f]{4})").matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    String.valueOf((char) Integer.parseInt(matcher.group(1), 16))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Bỏ dòng chú thích ({@code --}): ghi chú ROLLBACK trong đầu script không tính là lệnh. */
    private static String code(String sql) {
        return sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    private byte[] bytes() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(SCRIPT)) {
            assertThat(in).as("không tìm thấy %s", SCRIPT).isNotNull();
            return in.readAllBytes();
        }
    }

    private String read() throws IOException {
        return new String(bytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
