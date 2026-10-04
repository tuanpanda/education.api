package com.education.base.migration;

import com.education.base.security.Permissions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V17 - cổng học sinh, Phase 0 stream A: V17_1 (DDL: {@code SYS_USERS.USER_TYPE}, {@code EDU_USER_STUDENT_LINKS},
 * {@code ROLE_STUDENT}) và V17_2 (DML: menu "Tài khoản học sinh" + chức năng + quyền ROLE_ADMIN).
 * V17_3 thuộc stream B (audit log, {@code V17_3AuditLogScriptTest}); ở đây chỉ kiểm tra thứ tự / menu chung.
 */
class V17PortalScriptTest {

    private static final String V17_1 = "/db/migration/V17_1__portal_user_type_student_link.sql";
    private static final String V17_2 = "/db/migration/V17_2__student_account_menu.sql";
    private static final String V17_3 = "/db/migration/V17_3__audit_log.sql";
    private static final String GUARD = "WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK";
    private static final Pattern STATEMENT = Pattern.compile(
            "^\\s*(DECLARE|BEGIN|MERGE|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|COMMENT|COMMIT|SELECT)\\b",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern DML = Pattern.compile(
            "\\b(INSERT\\s+INTO|DELETE\\s+FROM|MERGE\\s+INTO|UPDATE\\s+\\w+\\s+SET)\\b");

    // ------------------------------------------------------------------ common conventions

    @Test
    void bothScriptsAreGuardedAndEndWithCommitThenExit() throws IOException {
        for (String script : List.of(V17_1, V17_2)) {
            String code = code(read(script));

            int guard = code.indexOf(GUARD);
            assertThat(guard).as(script + ": thiếu " + GUARD).isGreaterThanOrEqualTo(0);
            Matcher first = STATEMENT.matcher(code);
            assertThat(first.find()).as(script).isTrue();
            assertThat(first.start()).as(script + ": guard phải đứng trước lệnh đầu tiên").isGreaterThan(guard);

            List<String> lines = code.lines().map(String::strip).filter(line -> !line.isEmpty())
                    .filter(line -> !line.toUpperCase(Locale.ROOT).startsWith("PROMPT")).toList();
            assertThat(lines.get(lines.size() - 1)).as(script).isEqualToIgnoringCase("EXIT");
            assertThat(lines.get(lines.size() - 2)).as(script).isEqualToIgnoringCase("COMMIT;");
        }
    }

    @Test
    void scriptsHaveRollbackNotesAndRunOrder() throws IOException {
        for (String script : List.of(V17_1, V17_2)) {
            String text = read(script);
            assertThat(text).as(script).contains("ROLLBACK (").contains("THU TU CHAY");
        }
    }

    @Test
    void noHardCodedIdsAndIdsComeFromSequences() throws IOException {
        for (String script : List.of(V17_1, V17_2)) {
            String upper = code(read(script)).toUpperCase(Locale.ROOT);
            assertThat(upper).as(script).doesNotContainPattern("VALUES\\s*\\(\\s*\\d");
            assertThat(upper).as(script).doesNotContainPattern("\\bID\\s*=\\s*\\d");
            assertThat(upper).as(script).doesNotContainPattern("PARENT_ID\\s*=\\s*\\d");
            assertThat(upper).as(script).doesNotContain("INSERT INTO SYS_MENUS");
            assertThat(upper).as(script).doesNotContain("SYS_AUDIT_LOGS");
        }
    }

    /**
     * Sau khi gộp stream A + B (feat/portal-p0): đúng ba script V17, thứ tự V17_1 -> V17_2 -> V17_3, và hai menu
     * mới dưới DIR_SYSTEM có thứ tự không trùng: MENU_STUDENT_ACCOUNT = 5, MENU_AUDIT_LOG = 6 (sau BANK_ACCOUNT = 4).
     */
    @Test
    void v17ScriptsAndSystemMenuSortOrdersAreConsistent() throws Exception {
        URL marker = getClass().getResource(V17_1);
        assertThat(marker).isNotNull();
        Path dir = Path.of(marker.toURI()).getParent();
        try (Stream<Path> files = Files.list(dir)) {
            List<String> v17 = files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("V17_")).sorted()
                    .collect(Collectors.toList());
            assertThat(v17).containsExactly("V17_1__portal_user_type_student_link.sql",
                    "V17_2__student_account_menu.sql", "V17_3__audit_log.sql");
        }
        String v17_2 = code(read(V17_2));
        String v17_3 = code(read(V17_3));
        assertThat(v17_2).containsPattern("'MENU_STUDENT_ACCOUNT' MENU_CODE[\\s\\S]*?\\b5 SORT_ORDER");
        assertThat(v17_3).containsPattern("'MENU_AUDIT_LOG' MENU_CODE[\\s\\S]*?\\b6 SORT_ORDER");
        assertThat(v17_3).contains("p.MENU_CODE = 'DIR_SYSTEM'");
        // V17_3 (stream B) không phụ thuộc đối tượng của V17_1 (không FK sang cột / bảng mới).
        assertThat(v17_3.toUpperCase(Locale.ROOT)).doesNotContain("EDU_USER_STUDENT_LINKS")
                .doesNotContain("MENU_STUDENT_ACCOUNT");
        // Hai script có chữ tiếng Việt đều đòi NLS_LANG AL32UTF8.
        assertThat(read(V17_2)).contains("AMERICAN_AMERICA.AL32UTF8");
        assertThat(read(V17_3)).contains("AMERICAN_AMERICA.AL32UTF8");
    }

    // ------------------------------------------------------------------ V17_1

    /** V17_1 thuần ASCII (chữ có dấu viết bằng UNISTR) - không phụ thuộc NLS_LANG. */
    @Test
    void v17_1IsAsciiOnly() throws IOException {
        assertThat(read(V17_1).chars().filter(ch -> ch > 127).count()).isZero();
    }

    @Test
    void v17_1AddsUserTypeColumnWithDefaultAndCheck() throws IOException {
        String upper = flat(code(read(V17_1)));
        assertThat(upper).contains("ALTER TABLE SYS_USERS ADD USER_TYPE VARCHAR2(20) DEFAULT 'STAFF' NOT NULL");
        assertThat(upper).contains("CONSTRAINT CK_USERS_USER_TYPE CHECK (USER_TYPE IN ('STAFF', 'STUDENT', 'PARENT'))");
    }

    @Test
    void v17_1CreatesLinkTableWithKeysAndSingleActiveSelfIndex() throws IOException {
        String upper = flat(code(read(V17_1)));
        assertThat(upper)
                .contains("CREATE TABLE EDU_USER_STUDENT_LINKS")
                .contains("CREATE SEQUENCE SEQ_EDU_USER_STUDENT_LINKS")
                .contains("CREATE SEQUENCE SEQ_STUDENT_USERNAME")
                .contains("FOREIGN KEY (USER_ID) REFERENCES SYS_USERS (ID)")
                .contains("FOREIGN KEY (STUDENT_ID) REFERENCES EDU_STUDENTS (ID)")
                .contains("CONSTRAINT UQ_USL_USER_STUDENT UNIQUE (USER_ID, STUDENT_ID)")
                .contains("CHECK (RELATION IN ('SELF', 'PARENT'))")
                .contains("IS_PRIMARY NUMBER(1) DEFAULT 0 NOT NULL")
                .contains("STATUS VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL")
                .contains("CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL")
                .contains("UPDATED_AT TIMESTAMP")
                .contains("CREATED_BY VARCHAR2(50)")
                .contains("UPDATED_BY VARCHAR2(50)")
                .contains("CREATE UNIQUE INDEX UX_USL_SELF_ACTIVE ON EDU_USER_STUDENT_LINKS ( CASE WHEN "
                        + "RELATION = 'SELF' AND STATUS = 'ACTIVE' AND IS_DELETED = 0 THEN STUDENT_ID END)");
    }

    /** Mọi DDL đều nằm trong khối kiểm tra từ điển dữ liệu (chạy lại an toàn). */
    @Test
    void v17_1DdlIsIdempotent() throws IOException {
        String code = code(read(V17_1));
        String upper = code.toUpperCase(Locale.ROOT);
        assertThat(withoutStringLiterals(upper)).as("DDL ngoài EXECUTE IMMEDIATE (không kiểm tra tồn tại)")
                .doesNotContainPattern("\\b(CREATE|ALTER|DROP|TRUNCATE)\\b");
        assertThat(upper).contains("FROM USER_TAB_COLUMNS", "FROM USER_CONSTRAINTS", "FROM USER_TABLES",
                "FROM USER_INDEXES", "FROM USER_SEQUENCES");
        int ddl = count(upper, "EXECUTE IMMEDIATE");
        assertThat(ddl).isGreaterThanOrEqualTo(3);
        assertThat(upper).doesNotContain("DROP ");
        // Seed duy nhất: MERGE theo ROLE_CODE, không chèn trùng.
        assertThat(dmlTargets(upper)).containsExactly("SYS_ROLES");
        assertThat(flat(upper)).contains("MERGE INTO SYS_ROLES T USING (SELECT 'ROLE_STUDENT' ROLE_CODE FROM DUAL) S "
                + "ON (T.ROLE_CODE = S.ROLE_CODE) WHEN NOT MATCHED THEN INSERT");
        assertThat(upper).contains("SEQ_SYS_ROLES.NEXTVAL");
    }

    @Test
    void v17_1SeedsStudentRoleMatchingPermissionsConstant() throws IOException {
        assertThat(read(V17_1)).contains("'" + Permissions.STUDENT_ROLE + "'");
    }

    // ------------------------------------------------------------------ V17_2

    @Test
    void v17_2IsValidUtf8Nfc() throws IOException {
        byte[] bytes = bytes(V17_2);
        assertThat(bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB)
                .as("không có BOM").isFalse();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new AssertionError("V17_2 không phải UTF-8 hợp lệ", ex);
        }
        assertThat(Normalizer.isNormalized(text, Normalizer.Form.NFC)).isTrue();
    }

    @Test
    void v17_2CreatesStudentAccountMenuUnderSystemDirectory() throws IOException {
        String code = code(read(V17_2));
        assertThat(code).contains("'MENU_STUDENT_ACCOUNT' MENU_CODE, 'Tài khoản học sinh' MENU_NAME");
        assertThat(code).contains("'/system/student-accounts' PATH");
        assertThat(code).contains("p.MENU_CODE = 'DIR_SYSTEM'");
        assertThat(code).contains("UNISTR('T\\00E0i kho\\1EA3n h\\1ECDc sinh')");
        assertThat(code).contains("SEQ_SYS_MENUS.NEXTVAL", "SEQ_SYS_FUNCTIONS.NEXTVAL", "SEQ_SYS_ROLE_MENU_PERM.NEXTVAL");
    }

    @Test
    void v17_2FunctionsMatchPermissionConstantsAndAreGrantedToAdmin() throws IOException {
        String code = code(read(V17_2));
        for (String permission : List.of(Permissions.STUDENT_ACCOUNT_VIEW, Permissions.STUDENT_ACCOUNT_CREATE,
                Permissions.STUDENT_ACCOUNT_RESET_PASSWORD, Permissions.STUDENT_ACCOUNT_LOCK)) {
            String[] parts = permission.split(Permissions.SEPARATOR);
            assertThat(parts[0]).isEqualTo("MENU_STUDENT_ACCOUNT");
            assertThat(code).as(permission).containsPattern("SELECT '" + parts[1] + "'");
        }
        assertThat(code).contains("r.ROLE_CODE = 'ROLE_ADMIN'");
        assertThat(code).doesNotContain("ROLE_STUDENT");
    }

    @Test
    void v17_2IsDataOnlyAndUsesMerge() throws IOException {
        String upper = code(read(V17_2)).toUpperCase(Locale.ROOT);
        assertThat(upper).doesNotContainPattern(
                "\\b(CREATE|ALTER|DROP|TRUNCATE)\\s+(TABLE|SEQUENCE|INDEX|TRIGGER|VIEW)\\b");
        assertThat(upper).doesNotContain("EXECUTE IMMEDIATE");
        assertThat(upper).doesNotContainPattern("\\bDELETE\\s+FROM\\b");
        assertThat(dmlTargets(upper)).containsExactly("SYS_MENUS", "SYS_FUNCTIONS", "SYS_ROLE_MENU_PERMISSIONS");
        assertThat(dmlStatements(upper)).allMatch(statement -> statement.startsWith("MERGE INTO"));
    }

    // ------------------------------------------------------------------ helpers

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    private static List<String> dmlStatements(String upper) {
        return DML.matcher(upper).results().map(match -> match.group(1).replaceAll("\\s+", " ")).toList();
    }

    private static List<String> dmlTargets(String upper) {
        return Pattern.compile("\\b(?:MERGE\\s+INTO|INSERT\\s+INTO|DELETE\\s+FROM)\\s+(\\w+)|\\bUPDATE\\s+(\\w+)\\s+SET\\b")
                .matcher(upper).results()
                .map(match -> match.group(1) != null ? match.group(1) : match.group(2)).toList();
    }

    /** Bỏ mọi chuỗi ký tự (q'[...]' và '...') - phần còn lại là lệnh chạy trực tiếp. */
    private static String withoutStringLiterals(String code) {
        return code.replaceAll("(?is)q'\\[.*?\\]'", "''").replaceAll("'(?:[^']|'')*'", "''");
    }

    /** Gộp mọi khoảng trắng thành một dấu cách, viết hoa. */
    private static String flat(String code) {
        return code.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String code(String sql) {
        return sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    private byte[] bytes(String script) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(script)) {
            assertThat(in).as("không tìm thấy %s", script).isNotNull();
            return in.readAllBytes();
        }
    }

    private String read(String script) throws IOException {
        return new String(bytes(script), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
