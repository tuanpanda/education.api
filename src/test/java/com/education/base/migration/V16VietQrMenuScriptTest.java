package com.education.base.migration;

import com.education.base.security.Permissions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V16 gỡ menu "Thu học phí VietQR" ({@code MENU_TUITION_PAYMENT}, {@code /finance/vietqr}) vốn mở cùng màn hình
 * "Khoản học phí": V16_1 (chạy trước deploy) chỉ THÊM {@code MENU_TUITION_FEE:GEN_QR} và chuyển quyền; V16_2
 * (chạy sau deploy) kiểm tra V16_1 rồi xóa mềm menu cũ như {@code MenuAdminServiceImpl.delete}.
 */
class V16VietQrMenuScriptTest {

    private static final String V16_1 = "/db/migration/V16_1__move_gen_qr_to_tuition_fee.sql";
    private static final String V16_2 = "/db/migration/V16_2__remove_vietqr_menu.sql";
    private static final String OLD_MENU = "MENU_TUITION_PAYMENT";
    private static final Pattern DML = Pattern.compile(
            "\\b(INSERT\\s+INTO|DELETE\\s+FROM|MERGE\\s+INTO|UPDATE\\s+\\w+\\s+SET)\\b");

    @Test
    void genQrPermissionLivesOnTuitionFeeMenu() {
        assertThat(Permissions.TUITION_FEE_GEN_QR).isEqualTo(Permissions.of("MENU_TUITION_FEE", "GEN_QR"));
    }

    @Test
    void noPermissionConstantPointsAtRemovedMenu() throws IllegalAccessException {
        for (Field field : Permissions.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                assertThat((String) field.get(null)).as(field.getName())
                        .doesNotStartWith(OLD_MENU + Permissions.SEPARATOR);
            }
        }
    }

    @Test
    void bothScriptsAreGuardedDataOnlyAndCommit() throws IOException {
        for (String script : List.of(V16_1, V16_2)) {
            String code = code(read(script));
            String upper = code.toUpperCase(Locale.ROOT);

            int guard = code.indexOf("WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK");
            assertThat(guard).as(script).isGreaterThanOrEqualTo(0);
            assertThat(firstDml(upper)).as(script).isGreaterThan(guard);
            assertThat(upper).as(script)
                    .doesNotContainPattern("\\b(CREATE|ALTER|DROP|TRUNCATE)\\s+(TABLE|SEQUENCE|INDEX|TRIGGER|VIEW)\\b");

            List<String> lines = code.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
            assertThat(lines.get(lines.size() - 1)).as(script).isEqualToIgnoringCase("EXIT");
            assertThat(lines).as(script).anyMatch(line -> line.equalsIgnoreCase("COMMIT;"));
            assertThat(code.lastIndexOf("COMMIT;")).as(script).isGreaterThan(lastDml(upper));
        }
    }

    /** Thuần ASCII: không phụ thuộc NLS_LANG của SQL*Plus khi người dùng chạy script. */
    @Test
    void scriptsAreAsciiOnly() throws IOException {
        for (String script : List.of(V16_1, V16_2)) {
            assertThat(read(script).chars().filter(ch -> ch > 127).count()).as(script).isZero();
        }
    }

    @Test
    void v16_1OnlyAddsGenQrToTuitionFeeMenu() throws IOException {
        String code = code(read(V16_1));
        String upper = code.toUpperCase(Locale.ROOT);
        String[] permission = Permissions.TUITION_FEE_GEN_QR.split(Permissions.SEPARATOR);

        assertThat(upper).doesNotContainPattern("\\bDELETE\\s+FROM\\b")
                .doesNotContainPattern("\\bUPDATE\\s+\\w+\\s+SET\\b")
                .doesNotContainPattern("IS_DELETED\\s*=\\s*1")
                .doesNotContainPattern("IS_HIDDEN\\s*=\\s*1");
        assertThat(dmlTargets(upper)).containsExactly("SYS_FUNCTIONS", "SYS_ROLE_MENU_PERMISSIONS");
        assertThat(code).contains("WHERE m.MENU_CODE = '" + permission[0] + "'")
                .contains("'" + permission[1] + "' AS FUNCTION_CODE")
                .contains("T_CODES('VIEW', 'GEN_QR')")
                .contains("SEQ_SYS_FUNCTIONS.NEXTVAL")
                .contains("SEQ_SYS_ROLE_MENU_PERM.NEXTVAL");
        // Chỉ nối mã còn thiếu, không ghi đè quyền admin đã chỉnh.
        assertThat(code).containsPattern("WHERE INSTR\\([^;]*ALLOWED_FUNCTIONS[^;]*\\) = 0");
    }

    @Test
    void v16_2ChecksV16_1ThenSoftDeletesOnlyTheOldMenu() throws IOException {
        String code = code(read(V16_2));
        String upper = code.toUpperCase(Locale.ROOT);

        int precheck = upper.indexOf("RAISE_APPLICATION_ERROR(-20001");
        assertThat(precheck).isGreaterThan(0).isLessThan(firstDml(upper));
        assertThat(upper.substring(0, firstDml(upper))).contains("'MENU_TUITION_FEE'").contains("'GEN_QR'");

        assertThat(upper).doesNotContain("MERGE INTO").doesNotContain("INSERT INTO");
        assertThat(dmlTargets(upper)).containsExactly("SYS_ROLE_MENU_PERMISSIONS", "SYS_FUNCTIONS", "SYS_MENUS");
        assertThat(upper).containsPattern("\\bDELETE\\s+FROM\\s+SYS_ROLE_MENU_PERMISSIONS\\b")
                .doesNotContainPattern("\\bDELETE\\s+FROM\\s+SYS_(MENUS|FUNCTIONS)\\b")
                .containsPattern("IS_DELETED\\s*=\\s*1")
                .containsPattern("IS_HIDDEN\\s*=\\s*1");
        for (String statement : dmlStatements(upper)) {
            assertThat(statement).contains("'" + OLD_MENU + "'");
        }
    }

    private static int firstDml(String upper) {
        Matcher matcher = DML.matcher(upper);
        assertThat(matcher.find()).as("DML").isTrue();
        return matcher.start();
    }

    private static int lastDml(String upper) {
        Matcher matcher = DML.matcher(upper);
        int last = -1;
        while (matcher.find()) {
            last = matcher.start();
        }
        return last;
    }

    /** Bảng đích của từng câu DML theo thứ tự xuất hiện. */
    private static List<String> dmlTargets(String upper) {
        Matcher matcher = Pattern.compile("\\b(?:INSERT\\s+INTO|DELETE\\s+FROM|MERGE\\s+INTO|UPDATE)\\s+(SYS_\\w+)")
                .matcher(upper);
        List<String> targets = new ArrayList<>();
        while (matcher.find()) {
            targets.add(matcher.group(1));
        }
        return targets;
    }

    private static List<String> dmlStatements(String upper) {
        Matcher matcher = DML.matcher(upper);
        List<String> statements = new ArrayList<>();
        while (matcher.find()) {
            int end = upper.indexOf(';', matcher.start());
            statements.add(upper.substring(matcher.start(), end < 0 ? upper.length() : end));
        }
        return statements;
    }

    /** Bỏ dòng chú thích ({@code --}): ghi chú ROLLBACK trong đầu script không tính là lệnh. */
    private static String code(String sql) {
        return sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    private String read(String script) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(script)) {
            assertThat(in).as("không tìm thấy %s", script).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
