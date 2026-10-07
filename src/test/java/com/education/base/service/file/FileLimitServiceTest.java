package com.education.base.service.file;

import com.education.base.config.FileStorageProperties;
import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.dto.response.FileQuotaDto;
import com.education.base.dto.response.FileUploadPolicyDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileRepository;
import com.education.base.storage.BlobStore;
import com.education.base.storage.StorageUsage;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileLimitServiceTest {

    private static final long MB = 1024L * 1024L;

    private FileStorageProperties props;
    private FileRepository repo;
    private BlobStore blobStore;
    private FileSettingsSource source;
    private ObjectProvider<FileSettingsSource> provider;
    private MutableClock clock;
    private FileLimitService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        props = new FileStorageProperties();
        repo = mock(FileRepository.class);
        blobStore = mock(BlobStore.class);
        when(blobStore.usage()).thenReturn(new StorageUsage("LOCAL", 100L * 1024 * MB, 50L * 1024 * MB, -1));
        source = mock(FileSettingsSource.class);
        when(source.moduleSetting(anyString())).thenReturn(Optional.empty());
        when(source.quota(anyString(), any())).thenReturn(Optional.empty());
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(source);
        clock = new MutableClock();
        FileModuleRegistry registry = new FileModuleRegistry(List.of(new CommonFileModule(),
                new StudentImportFileModule()));
        service = new FileLimitService(props, provider, repo, blobStore, registry, clock);
    }

    @Test
    void yamlDefaults_whenNoSettingsSourceBean() {
        when(provider.getIfAvailable()).thenReturn(null);
        FileLimitService.ModuleLimits l = service.limits("CLASS");
        assertThat(l.maxFileSizeBytes()).isEqualTo(20 * MB);
        assertThat(l.maxFilesPerRef()).isEqualTo(30);
        assertThat(l.maxTotalBytesPerRef()).isEqualTo(200 * MB);
        assertThat(l.trashRetentionDays()).isEqualTo(30);
        assertThat(l.active()).isTrue();
        assertThat(service.limits("STUDENT").countToStudentQuota()).isTrue();
        assertThat(service.limits("STUDENT_IMPORT").allowedExtensions()).containsExactly("xlsx");
        assertThat(service.limits("STUDENT_IMPORT").maxFileSizeBytes()).isEqualTo(5 * MB);
        assertThat(service.limits("LIBRARY").maxFileSizeBytes()).isEqualTo(50 * MB);
        assertThat(service.globalQuota()).isEqualTo(50L * 1024 * MB);
        assertThat(service.studentQuota(1L)).isEqualTo(500 * MB);
    }

    @Test
    void dbSettings_overrideYaml_andExtensionsAreIntersectedWithSafeList() {
        when(source.moduleSetting("CLASS")).thenReturn(Optional.of(FileModuleSettingDto.builder().moduleName("CLASS")
                .maxFileSizeBytes(MB).maxFilesPerRef(2).allowedExtensions(List.of("pdf", "svg", "PNG"))
                .trashRetentionDays(7).active(true).build()));
        FileLimitService.ModuleLimits l = service.limits("CLASS");
        assertThat(l.maxFileSizeBytes()).isEqualTo(MB);
        assertThat(l.maxFilesPerRef()).isEqualTo(2);
        assertThat(l.maxTotalBytesPerRef()).isNull();
        assertThat(l.allowedExtensions()).containsExactly("pdf", "png");
        assertThat(l.trashRetentionDays()).isEqualTo(7);
    }

    @Test
    void dbQuota_overridesYaml_studentSpecificBeforeDefault() {
        when(source.quota("GLOBAL", null)).thenReturn(Optional.of(quota(10 * MB)));
        when(source.quota("STUDENT_DEFAULT", null)).thenReturn(Optional.of(quota(3 * MB)));
        when(source.quota("STUDENT", 9L)).thenReturn(Optional.of(quota(7 * MB)));
        assertThat(service.globalQuota()).isEqualTo(10 * MB);
        assertThat(service.studentQuota(8L)).isEqualTo(3 * MB);
        assertThat(service.studentQuota(9L)).isEqualTo(7 * MB);
    }

    @Test
    void settingsAreCachedFor60Seconds() {
        service.limits("CLASS");
        service.limits("CLASS");
        verify(source, times(1)).moduleSetting("CLASS");
        clock.advance(61_000);
        service.limits("CLASS");
        verify(source, times(2)).moduleSetting("CLASS");
        service.evictCache();
        service.limits("CLASS");
        verify(source, times(3)).moduleSetting("CLASS");
    }

    @Test
    void settingsSourceFailure_fallsBackToYaml() {
        when(source.moduleSetting("CLASS")).thenThrow(new RuntimeException("ORA-00942"));
        assertThat(service.limits("CLASS").maxFileSizeBytes()).isEqualTo(20 * MB);
    }

    @Test
    void check_eachErrorCode() throws Exception {
        assertCode(() -> service.check("CLASS", "CLASS", 1L, 20 * MB + 1, "pdf", null), "FILE_TOO_LARGE");
        assertCode(() -> service.check("CLASS", "CLASS", 1L, 10, "svg", null), "FILE_TYPE_NOT_ALLOWED");
        assertCode(() -> service.check("CLASS", "CLASS", 1L, 10, null, null), "FILE_TYPE_NOT_ALLOWED");

        when(repo.countByModuleNameAndReferenceIdAndStatusIn(eq("CLASS"), eq(1L), any())).thenReturn(30L);
        assertCode(() -> service.check("CLASS", "CLASS", 1L, 10, "pdf", null), "FILE_LIMIT_COUNT");
        when(repo.countByModuleNameAndReferenceIdAndStatusIn(eq("CLASS"), eq(1L), any())).thenReturn(1L);

        when(repo.sumSizeByRef(eq("CLASS"), eq(1L), any())).thenReturn(200 * MB - 5);
        assertCode(() -> service.check("CLASS", "CLASS", 1L, 10, "pdf", null), "FILE_LIMIT_TOTAL");
        when(repo.sumSizeByRef(eq("CLASS"), eq(1L), any())).thenReturn(0L);

        when(repo.sumSizeByOwnerStudent(eq(5L), any())).thenReturn(500 * MB);
        assertCode(() -> service.check("STUDENT", "STUDENT", 5L, 10, "pdf", 5L), "FILE_QUOTA_EXCEEDED");
        // CLASS không tính quota học sinh.
        assertThatCode(() -> service.check("CLASS", "CLASS", 1L, 10, "pdf", 5L)).doesNotThrowAnyException();

        when(repo.sumSizeByStatusIn(any())).thenReturn(50L * 1024 * MB);
        assertCode(() -> service.check("CLASS", "CLASS", 1L, 10, "pdf", null), "FILE_QUOTA_EXCEEDED");
        when(repo.sumSizeByStatusIn(any())).thenReturn(0L);

        when(blobStore.usage()).thenReturn(new StorageUsage("LOCAL", 100L * 1024 * MB, 4L * 1024 * MB, -1));
        assertCode(() -> service.check("CLASS", "CLASS", 1L, 10, "pdf", null), "STORAGE_LOW_DISK");
    }

    @Test
    void messages_containActualLimits() {
        assertThatThrownBy(() -> service.check("CLASS", "CLASS", 1L, 20 * MB + 1, "pdf", null))
                .hasMessageStartingWith("File vượt quá 20 MB");
        assertThatThrownBy(() -> service.check("STUDENT_IMPORT", "STUDENT", null, 10, "pdf", null))
                .hasMessageContaining("Chỉ nhận: xlsx");
        when(repo.countByModuleNameAndReferenceIdAndStatusIn(eq("CLASS"), eq(1L), any())).thenReturn(30L);
        assertThatThrownBy(() -> service.check("CLASS", "CLASS", 1L, 10, "pdf", null)).hasMessageContaining("30 file");
    }

    @Test
    void check_disabledModule() {
        when(source.moduleSetting("CLASS")).thenReturn(Optional.of(FileModuleSettingDto.builder().moduleName("CLASS")
                .maxFileSizeBytes(MB).trashRetentionDays(30).active(false).build()));
        assertCode(() -> service.check("CLASS", "CLASS", 1L, 10, "pdf", null), "FILE_MODULE_DISABLED");
    }

    @Test
    void check_unknownDiskUsage_doesNotBlock() throws Exception {
        when(blobStore.usage()).thenReturn(new StorageUsage("S3", -1, -1, -1));
        assertThatCode(() -> service.check("CLASS", "CLASS", null, 10, "pdf", null)).doesNotThrowAnyException();
    }

    @Test
    void policies_forOneOrAllRegisteredModules() {
        List<FileUploadPolicyDto> one = service.policies("student_import");
        assertThat(one).singleElement().satisfies(p -> {
            assertThat(p.getModuleName()).isEqualTo("STUDENT_IMPORT");
            assertThat(p.getAllowedExtensions()).containsExactly("xlsx");
            assertThat(p.getInlineExtensions()).isEmpty();
            assertThat(p.isReferenceRequired()).isFalse();
        });
        List<FileUploadPolicyDto> all = service.policies(null);
        assertThat(all).extracting(FileUploadPolicyDto::getModuleName).containsExactly("COMMON", "STUDENT_IMPORT");
        assertThat(all.get(0).getInlineExtensions()).containsExactly("jpg", "jpeg", "png", "gif", "webp", "pdf");
        assertCode(() -> service.policies("PAYROLL"), "INVALID_MODULE");
    }

    private static FileQuotaDto quota(long bytes) {
        return FileQuotaDto.builder().maxBytes(bytes).build();
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode()).isEqualTo(code);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T00:00:00Z"));

        void advance(long millis) {
            now.updateAndGet(i -> i.plusMillis(millis));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
