package com.education.base.migration;

import com.education.base.entity.FileEntity;
import com.education.base.security.Permissions;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quy uoc migration V19 (Quan ly file + kho tai lieu - hop dong Luong 0): guard, ASCII, COMMIT + EXIT,
 * khong nhung mat khau, cot FileEntity khop V19_1, menu V19_3 khong an MENU_FILE_EXPLORER.
 * Chi Luong B sua lop nay (Luong D: V19DocumentLibraryScriptTest).
 */
class V19FilesScriptTest {

    static final String V19_1 = "/db/migration/V19_1__files_metadata_v2.sql";
    static final String V19_2 = "/db/migration/V19_2__files_settings_maintenance.sql";
    static final String V19_3 = "/db/migration/V19_3__file_manager_menu.sql";
    static final String V19_4 = "/db/migration/V19_4__document_library.sql";
    static final List<String> ALL = List.of(V19_1, V19_2, V19_3, V19_4);

    private static final String GUARD = "WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK";
    private static final Pattern STATEMENT = Pattern.compile(
            "^\\s*(DECLARE|BEGIN|MERGE|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|COMMENT|COMMIT|SELECT)\\b",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /** Cot co tu V1 - khong thuoc V19_1. */
    private static final Set<String> V1_COLUMNS = Set.of("ID", "ORIGINAL_NAME", "STORED_NAME", "FILE_PATH",
            "CONTENT_TYPE", "FILE_SIZE", "MODULE_NAME", "REFERENCE_ID", "IS_DELETED", "CREATED_AT", "UPDATED_AT",
            "CREATED_BY", "UPDATED_BY");

    @Test
    void allScriptsGuardedAsciiAndEndWithCommitThenExit() throws IOException {
        for (String script : ALL) {
            String text = read(script);
            assertThat(text.chars().filter(ch -> ch > 127).count()).as(script + ": phai thuan ASCII").isZero();
            String code = code(text);
            int guard = code.indexOf(GUARD);
            assertThat(guard).as(script + ": thieu " + GUARD).isGreaterThanOrEqualTo(0);
            Matcher first = STATEMENT.matcher(code);
            assertThat(first.find()).as(script).isTrue();
            assertThat(first.start()).as(script + ": guard truoc lenh dau").isGreaterThan(guard);

            List<String> lines = code.lines().map(String::strip).filter(line -> !line.isEmpty())
                    .filter(line -> !line.toUpperCase(Locale.ROOT).startsWith("PROMPT")).toList();
            assertThat(lines.get(lines.size() - 1)).as(script).isEqualToIgnoringCase("EXIT");
            assertThat(lines.get(lines.size() - 2)).as(script).isEqualToIgnoringCase("COMMIT;");

            assertThat(text).as(script).contains("ROLLBACK").contains("THU TU CHAY");
            assertThat(text).as(script)
                    .doesNotContainPattern("(?i)sqlplus(\\.exe)?\\s+(-\\w+\\s+)*\\w+/\\S+@");
        }
    }

    @Test
    void v19ScriptsOrdered() throws Exception {
        URL marker = getClass().getResource(V19_1);
        assertThat(marker).isNotNull();
        Path dir = Path.of(marker.toURI()).getParent();
        try (Stream<Path> files = Files.list(dir)) {
            List<String> v19 = files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("V19_"))
                    .sorted().toList();
            assertThat(v19).containsExactly(
                    "V19_1__files_metadata_v2.sql",
                    "V19_2__files_settings_maintenance.sql",
                    "V19_3__file_manager_menu.sql",
                    "V19_4__document_library.sql");
        }
    }

    @Test
    void v19_1CoversEveryNewFileEntityColumnAndKeepsProcAliases() throws IOException {
        String text = read(V19_1);
        String upper = code(text).toUpperCase(Locale.ROOT);
        int added = 0;
        for (Field field : FileEntity.class.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column == null || V1_COLUMNS.contains(column.name())) {
                continue;
            }
            added++;
            assertThat(upper).as("V19_1 thieu cot " + column.name()).contains("ENSURE_COLUMN('" + column.name() + "'");
        }
        assertThat(added).as("so cot moi cua FileEntity").isEqualTo(15);
        assertThat(upper).contains("CREATE OR REPLACE PROCEDURE PRC_GET_FILES_BY_REF")
                .contains("STATUS = 'ACTIVE'")
                .contains("ORIGINAL_NAME AS ORIGINALNAME")
                .contains("VARCHAR2(255 CHAR)")
                .contains("CK_FILES_DELETED_SYNC");
    }

    @Test
    void v19_2CreatesSettingsQuotaAndScanTables() throws IOException {
        String upper = code(read(V19_2)).toUpperCase(Locale.ROOT);
        assertThat(upper).contains("SYS_FILE_MODULE_SETTINGS")
                .contains("SYS_FILE_QUOTAS")
                .contains("SYS_FILE_SCAN_RUNS")
                .contains("SYS_FILE_SCAN_ITEMS")
                .contains("SEQ_SYS_FILE_QUOTAS.NEXTVAL")
                .contains("UQ_FILE_SCAN_RUNS_RUNNING")
                .contains("ON DELETE CASCADE");
        // Seed chi them dong con thieu - khong ghi de cau hinh admin da sua.
        assertThat(upper).doesNotContain("WHEN MATCHED THEN UPDATE");
        for (String module : List.of("'STUDENT'", "'STUDENT_IMPORT'", "'CLASS'", "'LEAD'", "'TUITION'", "'COMMON'",
                "'ASSIGNMENT'", "'SUBMISSION'", "'FEEDBACK'")) {
            assertThat(upper).as("seed module " + module).contains(module);
        }
    }

    @Test
    void v19_3SeedsFileManagerMenuForAdminOnlyAndDoesNotHideFileExplorer() throws IOException {
        String text = read(V19_3);
        String upper = code(text).toUpperCase(Locale.ROOT);
        assertThat(text).contains("UNISTR('Qu\\1EA3n l\\00FD file')")
                .contains("'/system/files' PATH")
                .contains("DIR_SYSTEM");
        for (String fn : List.of("VIEW", "DOWNLOAD", "DELETE", "RESTORE", "PURGE", "CONFIG", "MAINTAIN")) {
            assertThat(upper).contains("'" + fn + "'");
        }
        assertThat(upper).doesNotContain("MENU_FILE_EXPLORER");
        assertThat(upper).doesNotContain("ROLE_TEACHER");
        assertThat(Permissions.FILE_MANAGER_VIEW).isEqualTo("MENU_FILE_MANAGER:VIEW");
        assertThat(Permissions.FILE_MANAGER_MAINTAIN).isEqualTo("MENU_FILE_MANAGER:MAINTAIN");
    }

    static String read(String classpath) throws IOException {
        try (InputStream in = V19FilesScriptTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String code(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\\R")) {
            if (line.strip().startsWith("--")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
