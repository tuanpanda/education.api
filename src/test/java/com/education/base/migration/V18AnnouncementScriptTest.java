package com.education.base.migration;

import com.education.base.entity.AnnouncementEntity;
import com.education.base.entity.AnnouncementReadEntity;
import com.education.base.security.Permissions;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V18 Stream B: DDL thong bao + menu MENU_ANNOUNCEMENT.
 */
class V18AnnouncementScriptTest {

    private static final String V18_1 = "/db/migration/V18_1__portal_announcements.sql";
    private static final String V18_2 = "/db/migration/V18_2__announcement_menus.sql";
    private static final String GUARD = "WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK";
    private static final Pattern STATEMENT = Pattern.compile(
            "^\\s*(DECLARE|BEGIN|MERGE|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|COMMENT|COMMIT|SELECT)\\b",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Test
    void bothScriptsAreGuardedAndEndWithCommitThenExit() throws IOException {
        for (String script : List.of(V18_1, V18_2)) {
            String code = code(read(script));
            int guard = code.indexOf(GUARD);
            assertThat(guard).as(script + ": thieu " + GUARD).isGreaterThanOrEqualTo(0);
            Matcher first = STATEMENT.matcher(code);
            assertThat(first.find()).as(script).isTrue();
            assertThat(first.start()).as(script + ": guard truoc lenh dau").isGreaterThan(guard);

            List<String> lines = code.lines().map(String::strip).filter(line -> !line.isEmpty())
                    .filter(line -> !line.toUpperCase(Locale.ROOT).startsWith("PROMPT")).toList();
            assertThat(lines.get(lines.size() - 1)).as(script).isEqualToIgnoringCase("EXIT");
            assertThat(lines.get(lines.size() - 2)).as(script).isEqualToIgnoringCase("COMMIT;");
        }
    }

    @Test
    void v18_1IsAsciiOnlyAndCreatesTables() throws IOException {
        String text = read(V18_1);
        assertThat(text.chars().filter(ch -> ch > 127).count()).isZero();
        String upper = code(text).toUpperCase(Locale.ROOT);
        assertThat(upper).contains("EDU_ANNOUNCEMENTS")
                .contains("EDU_ANNOUNCEMENT_READS")
                .contains("SEQ_EDU_ANNOUNCEMENTS")
                .contains("SCOPE_TYPE")
                .contains("AUDIENCE")
                .contains("IS_PINNED")
                .contains("PUBLISHED_AT")
                .contains("EXPIRES_AT")
                .contains("PRIMARY KEY (ANNOUNCEMENT_ID, USER_ID)");
        assertThat(text).contains("ROLLBACK (").contains("THU TU CHAY");
        // Tieu de tieng Viet: @Size(max = 200) tinh theo ky tu -> cot phai dung CHAR semantics.
        assertThat(upper).contains("TITLE         VARCHAR2(200 CHAR)");
    }

    @Test
    void v18ScriptsDoNotEmbedCredentials() throws IOException {
        for (String script : List.of(V18_1, V18_2)) {
            assertThat(read(script)).as(script).doesNotContainPattern("(?i)sqlplus(\\.exe)?\\s+(-\\w+\\s+)*\\w+/\\S+@");
        }
    }

    @Test
    void v18_2SeedsMenuWithUnistrAndRoleGrants() throws IOException {
        String text = read(V18_2);
        assertThat(text.chars().filter(ch -> ch > 127).count()).isZero();
        assertThat(text).contains("MENU_ANNOUNCEMENT")
                .contains("UNISTR('Th\\00F4ng b\\00E1o')")
                .contains("DIR_ACADEMIC")
                .contains("'PUBLISH'")
                .contains("ROLE_ADMIN")
                .contains("ROLE_TEACHER")
                .contains("AMERICAN_AMERICA.AL32UTF8")
                // Cung nhom /academic/* voi cac menu hoc vu khac; frontend route /academic/announcements.
                .contains("'/academic/announcements' PATH");
        assertThat(Permissions.ANNOUNCEMENT_VIEW).isEqualTo("MENU_ANNOUNCEMENT:VIEW");
        assertThat(Permissions.ANNOUNCEMENT_PUBLISH).isEqualTo("MENU_ANNOUNCEMENT:PUBLISH");
    }

    @Test
    void v18ScriptsOrdered() throws Exception {
        URL marker = getClass().getResource(V18_1);
        assertThat(marker).isNotNull();
        Path dir = Path.of(marker.toURI()).getParent();
        try (Stream<Path> files = Files.list(dir)) {
            List<String> v18 = files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("V18_"))
                    .sorted().collect(Collectors.toList());
            assertThat(v18).containsExactly(
                    "V18_1__portal_announcements.sql",
                    "V18_2__announcement_menus.sql");
        }
    }

    @Test
    void entityColumnsCoveredByV18_1() throws IOException {
        String upper = code(read(V18_1)).toUpperCase(Locale.ROOT);
        for (String col : columnNames(AnnouncementEntity.class)) {
            assertThat(upper).as("EDU_ANNOUNCEMENTS thieu cot " + col).contains(col);
        }
        for (String col : columnNames(AnnouncementReadEntity.class)) {
            assertThat(upper).as("EDU_ANNOUNCEMENT_READS thieu cot " + col).contains(col);
        }
        assertThat(AnnouncementEntity.class.getAnnotation(Table.class).name()).isEqualTo("EDU_ANNOUNCEMENTS");
        assertThat(AnnouncementReadEntity.class.getAnnotation(Table.class).name())
                .isEqualTo("EDU_ANNOUNCEMENT_READS");
    }

    private static List<String> columnNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null && !column.name().isBlank()) {
                names.add(column.name().toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static String read(String classpath) throws IOException {
        try (InputStream in = V18AnnouncementScriptTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String code(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String stripped = line.strip();
            if (stripped.startsWith("--")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
