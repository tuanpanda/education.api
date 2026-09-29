package com.education.base.migration;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đảm bảo hash BCrypt seed trong V12 khớp với mật khẩu mặc định ghi trong README.
 */
class V12SeedPasswordTest {

    private static final String SCRIPT = "/db/migration/V12__system_admin_security.sql";

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

    private static String firstHashAfter(String sql, String marker) {
        int index = sql.indexOf(marker);
        assertThat(index).as(marker).isGreaterThanOrEqualTo(0);
        Matcher matcher = Pattern.compile("\\$2a\\$10\\$[./A-Za-z0-9]{53}").matcher(sql);
        assertThat(matcher.find(index)).isTrue();
        return matcher.group();
    }
}
