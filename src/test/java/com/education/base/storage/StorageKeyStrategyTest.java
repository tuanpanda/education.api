package com.education.base.storage;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageKeyStrategyTest {

    private final StorageKeyStrategy strategy = new StorageKeyStrategy(
            Clock.fixed(Instant.parse("2026-03-05T03:00:00Z"), ZoneId.of("Asia/Bangkok")));

    @Test
    void newKey_moduleYearMonthUuidExt() {
        StorageKeyStrategy.StorageKey k = strategy.newKey("student", null, "PDF");
        assertThat(k.key()).matches("STUDENT/2026/03/[0-9a-f]{32}\\.pdf");
        assertThat(k.key()).endsWith("/" + k.storedName());
    }

    @Test
    void newKey_withSubFolder() {
        assertThat(strategy.newKey("STUDENT", "import", "xlsx").key()).matches("STUDENT/IMPORT/2026/03/[0-9a-f]{32}\\.xlsx");
    }

    @Test
    void newKey_uniqueEachCall() {
        assertThat(strategy.newKey("A", null, "txt").key()).isNotEqualTo(strategy.newKey("A", null, "txt").key());
    }

    @Test
    void newKey_rejectsUnsafeSegments() {
        assertThatThrownBy(() -> strategy.newKey("../X", null, "pdf")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> strategy.newKey("A", "a/b", "pdf")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> strategy.newKey("A", null, "p.df")).isInstanceOf(IllegalArgumentException.class);
    }
}
