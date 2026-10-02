package com.education.base.migration;

import com.education.base.common.DomainConstants;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V15 chỉ thêm điều kiện ẩn lớp {@code CLOSED} / {@code CANCELLED} vào {@code PRC_GET_TIMETABLE_BY_RANGE};
 * mọi phần còn lại của procedure phải giữ nguyên bản V6 (tham số, cột, bộ lọc, sắp xếp, mã lỗi).
 */
class V15TimetableScriptTest {

    private static final String V6 = "/db/migration/V6__class_timetable.sql";
    private static final String V15 = "/db/migration/V15__timetable_hide_closed_classes.sql";
    private static final String PROC_START = "CREATE OR REPLACE PROCEDURE PRC_GET_TIMETABLE_BY_RANGE(";
    private static final String PROC_END = "END PRC_GET_TIMETABLE_BY_RANGE;";
    private static final String HIDE_FILTER = "AND c.STATUS NOT IN ('CLOSED', 'CANCELLED')";

    @Test
    void procedureIsV6PlusClosedClassFilterOnly() throws IOException {
        String v6 = procedure(read(V6));
        String v15 = procedure(read(V15));

        assertThat(v15).containsPattern(
                "WHERE s\\.IS_DELETED = 0\\n(\\s*--[^\\n]*\\n)?\\s*AND c\\.STATUS NOT IN \\('CLOSED', 'CANCELLED'\\)\\n");
        String withoutV15Lines = v15.lines()
                .filter(line -> !line.strip().equals(HIDE_FILTER) && !line.strip().startsWith("-- V15:"))
                .collect(Collectors.joining("\n"));
        assertThat(withoutV15Lines).isEqualTo(v6.lines().collect(Collectors.joining("\n")));
    }

    @Test
    void hiddenStatusesMatchDomainConstants() {
        assertThat(HIDE_FILTER).contains("'" + DomainConstants.CLASS_STATUS_CLOSED + "'")
                .contains("'" + DomainConstants.CLASS_STATUS_CANCELLED + "'");
        assertThat(List.of("CLOSED", "CANCELLED")).allMatch(DomainConstants::isHiddenFromTimetable);
        assertThat(List.of("PLANNED", "OPEN", "ONGOING")).noneMatch(DomainConstants::isHiddenFromTimetable);
        assertThat(DomainConstants.isHiddenFromTimetable(null)).isFalse();
    }

    @Test
    void scriptOnlyReplacesProcedureAndVerifiesIt() throws IOException {
        String sql = read(V15);
        String code = sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        String upper = code.toUpperCase(Locale.ROOT);

        assertThat(code.indexOf("WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK"))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(code.indexOf(PROC_START));
        assertThat(code).contains("SHOW ERRORS PROCEDURE PRC_GET_TIMETABLE_BY_RANGE")
                .contains("FROM USER_OBJECTS");
        assertThat(upper).doesNotContainPattern("\\b(ALTER|DROP|TRUNCATE)\\s+TABLE\\b")
                .doesNotContainPattern("\\b(INSERT\\s+INTO|DELETE\\s+FROM|MERGE\\s+INTO)\\b")
                .doesNotContainPattern("\\bUPDATE\\s+\\w+\\s+SET\\b");
        List<String> lines = code.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        assertThat(lines.get(lines.size() - 1)).isEqualToIgnoringCase("EXIT");
        assertThat(lines).anyMatch(line -> line.equalsIgnoreCase("COMMIT;"));
    }

    private static String procedure(String sql) {
        int start = sql.indexOf(PROC_START);
        int end = sql.indexOf(PROC_END, start);
        assertThat(start).as("PRC_GET_TIMETABLE_BY_RANGE").isGreaterThanOrEqualTo(0);
        assertThat(end).as(PROC_END).isGreaterThan(start);
        return sql.substring(start, end + PROC_END.length());
    }

    private String read(String script) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(script)) {
            assertThat(in).as("không tìm thấy %s", script).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}