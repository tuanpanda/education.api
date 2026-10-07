package com.education.base.service.file;

import com.education.base.entity.FileModuleSettingEntity;
import com.education.base.entity.FileQuotaEntity;
import com.education.base.repository.FileModuleSettingRepository;
import com.education.base.repository.FileQuotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbFileSettingsSourceTest {

    private final FileModuleSettingRepository modules = mock(FileModuleSettingRepository.class);
    private final FileQuotaRepository quotas = mock(FileQuotaRepository.class);
    private final MutableClock clock = new MutableClock();
    private DbFileSettingsSource source;

    @BeforeEach
    void setUp() {
        when(modules.findAll()).thenReturn(List.of(FileModuleSettingEntity.builder()
                .moduleName("STUDENT").maxFileSizeBytes(20_971_520L).maxFilesPerRef(30)
                .allowedExtensions(" PDF, jpg ,,pdf").trashRetentionDays(30).countToStudentQuota(1).isActive(1)
                .createdBy("V19_2_MIGRATION").build()));
        when(quotas.findAll()).thenReturn(List.of(
                FileQuotaEntity.builder().id(1L).scopeType("GLOBAL").maxBytes(50L << 30).warnPercent(80).build(),
                FileQuotaEntity.builder().id(2L).scopeType("STUDENT").scopeId(12L).maxBytes(1000L).warnPercent(90)
                        .build()));
        source = new DbFileSettingsSource(modules, quotas, clock);
    }

    @Test
    void mapsRowsAndLooksUpCaseInsensitive() {
        var setting = source.moduleSetting("student").orElseThrow();
        assertThat(setting.getAllowedExtensions()).containsExactly("pdf", "jpg");
        assertThat(setting.isCountToStudentQuota()).isTrue();
        assertThat(setting.isActive()).isTrue();
        assertThat(setting.getUpdatedBy()).isEqualTo("V19_2_MIGRATION");
        assertThat(source.moduleSetting("LEAD")).isEmpty();
        assertThat(source.quota("GLOBAL", null)).get().extracting("maxBytes").isEqualTo(50L << 30);
        assertThat(source.quota("STUDENT", 12L)).get().extracting("warnPercent").isEqualTo(90);
        assertThat(source.quota("STUDENT", 13L)).isEmpty();
        assertThat(source.moduleSettings()).hasSize(1);
    }

    @Test
    void cachesFor60SecondsThenReloads() {
        source.moduleSettings();
        clock.plusSeconds(59);
        source.moduleSetting("STUDENT");
        source.quota("GLOBAL", null);
        verify(modules, times(1)).findAll();
        clock.plusSeconds(2);
        source.moduleSettings();
        verify(modules, times(2)).findAll();
    }

    @Test
    void evictForcesReload() {
        source.moduleSettings();
        source.evict();
        source.moduleSettings();
        verify(quotas, times(2)).findAll();
    }

    @Test
    void dbErrorFallsBackToEmptyAndRetriesSoon() {
        when(modules.findAll()).thenThrow(new RuntimeException("ORA-00942"));
        DbFileSettingsSource broken = new DbFileSettingsSource(modules, quotas, clock);
        assertThat(broken.moduleSetting("STUDENT")).isEmpty();
        clock.plusSeconds(10);
        broken.moduleSettings();
        verify(modules, times(2)).findAll();
    }

    @Test
    void parseExtensionsBlankIsNull() {
        assertThat(DbFileSettingsSource.parseExtensions(null)).isNull();
        assertThat(DbFileSettingsSource.parseExtensions(" , ")).isNull();
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T03:00:00Z");

        void plusSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Bangkok");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
