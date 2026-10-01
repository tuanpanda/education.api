package com.education.base.migration;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đảm bảo hash BCrypt seed trong V12 khớp với mật khẩu tạm thời ghi trong chú thích của V12.
 */
class V12SeedPasswordTest {

    private static final String SCRIPT = "/db/migration/V12__system_admin_security.sql";

    /** Hash "mau" cua schema_init.sql cu: dung dinh dang BCrypt nhung khong khop mat khau nao. */
    private static final String LEGACY_PLACEHOLDER_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO96u8xLw9Jm9j.qR8xT6rW1dG7z5Kq2G";

    private static final Pattern BCRYPT_10 = Pattern.compile("\\$2a\\$10\\$[./A-Za-z0-9]{53}");

    @Test
    void seededHashes_matchDocumentedPasswords() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(SCRIPT)) {
            assertThat(in).as("V12 migration trên classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String adminHash = firstHashAfter(sql, "-- BCrypt (cost 10) cua 'Admin@123'.");
        assertThat(encoder.matches("Admin@123", adminHash)).isTrue();

        String demoHash = firstHashAfter(sql, "-- Tai khoan demo (V1)");
        assertThat(encoder.matches("Education@123", demoHash)).isTrue();

        assertThat(sql).contains("MUST_CHANGE_PASSWORD = 1");
        assertThat(sql).contains("'/admin/users'").contains("'/admin/roles'").contains("'/admin/menus'");
    }

    /**
     * schema_init.sql cu seed admin/teacher1 bang mot hash BCrypt "mau" khong khop mat khau nao. V12 phai
     * ghi de ca hash nay (khong chi hash SHA-256), neu khong admin / Admin@123 khong dang nhap duoc.
     */
    @Test
    void legacyPlaceholderHash_isReplacedByV12() throws IOException {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("Admin@123", LEGACY_PLACEHOLDER_HASH)).isFalse();
        assertThat(encoder.matches("Education@123", LEGACY_PLACEHOLDER_HASH)).isFalse();

        String sql = read(SCRIPT);
        String adminMerge = between(sql, "-- BCrypt (cost 10) cua 'Admin@123'.", "WHEN NOT MATCHED");
        assertThat(adminMerge).contains("OR t.PASSWORD_HASH = '" + LEGACY_PLACEHOLDER_HASH + "'");

        String demoUpdate = between(sql, "-- Tai khoan demo (V1)", "PROMPT");
        assertThat(demoUpdate).contains("OR PASSWORD_HASH = '" + LEGACY_PLACEHOLDER_HASH + "'");
    }

    @Test
    void schemaInit_seedsOnlyUsableHashes() throws IOException {
        String sql = read("/db/schema_init.sql");
        assertThat(sql).doesNotContain(LEGACY_PLACEHOLDER_HASH);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Matcher matcher = BCRYPT_10.matcher(sql);
        while (matcher.find()) {
            String hash = matcher.group();
            assertThat(encoder.matches("Admin@123", hash) || encoder.matches("Education@123", hash))
                    .as("schema_init hash %s", hash).isTrue();
        }
    }

    /** Script van hanh, nam ngoai classpath (khong dong goi trong jar). */
    private static final Path FIX_SCRIPT = Path.of("scripts", "db", "fix_admin_password.sql");

    /** Chi dong con hash "mau" hoac khong phai BCrypt moi bi dong toi -> chay lai khong dat lai mat khau da doi. */
    private static final String ONLY_UNUSABLE_HASHES = "(PASSWORD_HASH = '" + LEGACY_PLACEHOLDER_HASH + "' "
            + "OR PASSWORD_HASH NOT LIKE '$2%')";

    @Test
    void fixAdminScript_isNotPackagedInJar() {
        // Kiểm tra cây nguồn (target/classes có thể còn bản cũ nếu không chạy mvn clean).
        assertThat(Files.exists(Path.of("src", "main", "resources", "db", "fix_admin_password.sql"))).isFalse();
        assertThat(Files.exists(FIX_SCRIPT)).as(FIX_SCRIPT + " ton tai").isTrue();
    }

    @Test
    void fixAdminScript_restoresV12SeedState() throws IOException {
        String sql = readFixScript();
        String hash = firstHashAfter(sql, "-- BCrypt (cost 10) cua 'Admin@123'");
        assertThat(new BCryptPasswordEncoder().matches("Admin@123", hash)).isTrue();
        assertThat(hash).isEqualTo(firstHashAfter(read(SCRIPT), "-- BCrypt (cost 10) cua 'Admin@123'."));
        assertThat(sql).contains("MUST_CHANGE_PASSWORD = 1").contains("STATUS = 'ACTIVE'")
                .contains("IS_DELETED = 0").contains("WHERE USERNAME = 'admin'").contains("'ROLE_ADMIN'")
                .contains("WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK");
    }

    /**
     * Chay lai script khong bao gio dat lai mat khau da doi: admin chi bi sua khi hash la hash "mau"
     * hoac khong phai BCrypt (khong con dieu kien "khac hash seed" / trang thai nhu truoc).
     */
    @Test
    void fixAdminScript_neverResetsAChangedPassword() throws IOException {
        String sql = readFixScript();
        String adminUpdate = normalize(between(sql, "WHERE USERNAME = 'admin'", ";"));
        assertThat(adminUpdate).contains(ONLY_UNUSABLE_HASHES);
        assertThat(adminUpdate).doesNotContain("PASSWORD_HASH <>").doesNotContain("MUST_CHANGE_PASSWORD <>")
                .doesNotContain("STATUS <>").doesNotContain("IS_DELETED <>");
    }

    /**
     * teacher1 (schema_init.sql cu) cung mang hash "mau" nhu admin, nen script sua phai dua ca tai khoan demo
     * con hash "mau" / khong phai BCrypt (SHA-256 V1) ve BCrypt 'Education@123' giong V12, bat buoc doi mat khau.
     */
    @Test
    void fixAdminScript_alsoRepairsDemoAccounts() throws IOException {
        String sql = readFixScript();
        String demoUpdate = normalize(between(sql, "-- Tai khoan demo", "COMMIT;"));
        String hash = firstHashAfter(demoUpdate, "UPDATE SYS_USERS");
        assertThat(new BCryptPasswordEncoder().matches("Education@123", hash)).isTrue();
        assertThat(hash).isEqualTo(firstHashAfter(read(SCRIPT), "-- Tai khoan demo (V1)"));
        assertThat(demoUpdate).contains("WHERE USERNAME <> 'admin'")
                .contains(ONLY_UNUSABLE_HASHES)
                .contains("MUST_CHANGE_PASSWORD = 1");
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ");
    }

    private static String readFixScript() throws IOException {
        return Files.readString(FIX_SCRIPT, StandardCharsets.UTF_8);
    }
    private String read(String resource) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as(resource + " tren classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String between(String sql, String startMarker, String endMarker) {
        int start = sql.indexOf(startMarker);
        assertThat(start).as(startMarker).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(endMarker, start);
        assertThat(end).as(endMarker).isGreaterThan(start);
        return sql.substring(start, end);
    }

    private static String firstHashAfter(String sql, String marker) {
        int index = sql.indexOf(marker);
        assertThat(index).as(marker).isGreaterThanOrEqualTo(0);
        Matcher matcher = BCRYPT_10.matcher(sql);
        assertThat(matcher.find(index)).isTrue();
        return matcher.group();
    }
}
