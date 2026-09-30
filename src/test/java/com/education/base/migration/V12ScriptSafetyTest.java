package com.education.base.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V12 chạy bằng sqlplus: phải dừng (và rollback DML) ở lỗi đầu tiên thay vì chạy tiếp các bước sau.
 */
class V12ScriptSafetyTest {

    private static final String SCRIPT = "/db/migration/V12__system_admin_security.sql";

    @Test
    void whenSqlErrorExit_isDeclaredBeforeFirstStatement() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(SCRIPT)) {
            assertThat(in).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int whenever = sql.indexOf("WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK");
        assertThat(whenever).as("WHENEVER SQLERROR trong V12").isPositive();
        assertThat(whenever).isLessThan(sql.indexOf("DECLARE"));
        assertThat(whenever).isLessThan(sql.indexOf("MERGE INTO"));
        assertThat(sql.stripTrailing()).endsWith("EXIT");
    }
}
