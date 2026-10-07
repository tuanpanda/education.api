package com.education.base.service.file;

import com.education.base.config.FileStorageProperties;
import com.education.base.config.FilesProperties;
import com.education.base.mapper.FileMapper;
import com.education.base.repository.*;
import com.education.base.security.FileAccessPolicy;
import com.education.base.service.AuditService;
import com.education.base.service.DocumentAccessService;
import com.education.base.service.FileAdminService;
import com.education.base.service.FileStorageService;
import com.education.base.service.impl.*;
import com.education.base.storage.LocalDiskBlobStore;
import com.education.base.storage.NoopVirusScanner;
import com.education.base.storage.StorageKeyStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Kiem tra wiring Spring cua he thong file (Luong A/B/D) khong can DB: bean that + repository gia.
 */
class FileSubsystemContextTest {

    @Configuration
    @EnableConfigurationProperties
    @Import({com.education.base.config.SchedulingConfig.class, FileStorageProperties.class, FilesProperties.class, LocalDiskBlobStore.class, NoopVirusScanner.class,
            StorageKeyStrategy.class, FileModuleRegistry.class, FileLimitService.class, DbFileSettingsSource.class,
            FileLifecycleServiceImpl.class, FileStorageServiceImpl.class, FileAccessPolicy.class,
            TeacherFileScope.class, ClassFileModule.class, CommonFileModule.class, LeadFileModule.class,
            StudentFileModule.class, StudentImportFileModule.class, TuitionFileModule.class, LibraryFileModule.class,
            DocumentAccessService.class, DocumentFolderServiceImpl.class, DocumentItemServiceImpl.class,
            DocumentShareServiceImpl.class, FileAdminServiceImpl.class, FileMaintenanceService.class,
            FileRetentionJob.class})
    static class Cfg {
        @Bean static org.springframework.core.convert.ConversionService conversionService() {
            return org.springframework.boot.convert.ApplicationConversionService.getSharedInstance();
        }
        @Bean FileRepository fileRepository() { return mock(FileRepository.class); }
        @Bean FileAdminRepository fileAdminRepository() { return mock(FileAdminRepository.class); }
        @Bean FileModuleSettingRepository fileModuleSettingRepository() { return mock(FileModuleSettingRepository.class); }
        @Bean FileQuotaRepository fileQuotaRepository() { return mock(FileQuotaRepository.class); }
        @Bean FileScanRunRepository fileScanRunRepository() { return mock(FileScanRunRepository.class); }
        @Bean FileScanItemRepository fileScanItemRepository() { return mock(FileScanItemRepository.class); }
        @Bean DocFolderRepository docFolderRepository() { return mock(DocFolderRepository.class); }
        @Bean DocItemRepository docItemRepository() { return mock(DocItemRepository.class); }
        @Bean DocShareRepository docShareRepository() { return mock(DocShareRepository.class); }
        @Bean ClassRepository classRepository() { return mock(ClassRepository.class); }
        @Bean ClassSessionRepository classSessionRepository() { return mock(ClassSessionRepository.class); }
        @Bean ClassStudentRepository classStudentRepository() { return mock(ClassStudentRepository.class); }
        @Bean StudentRepository studentRepository() { return mock(StudentRepository.class); }
        @Bean LeadRepository leadRepository() { return mock(LeadRepository.class); }
        @Bean TuitionFeeRepository tuitionFeeRepository() { return mock(TuitionFeeRepository.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean RoleRepository roleRepository() { return mock(RoleRepository.class); }
        @Bean AuditLogRepository auditLogRepository() { return mock(AuditLogRepository.class); }
        @Bean AuditService auditService() { return mock(AuditService.class); }
        @Bean TeachingAssignmentGuard teachingAssignmentGuard() { return mock(TeachingAssignmentGuard.class); }
        @Bean FileMapper fileMapper() { return mock(FileMapper.class); }
    }

    @Test
    void fileBeansWireTogether() {
        new ApplicationContextRunner()
                .withPropertyValues("app.storage.jobs.enabled=true")
                .withUserConfiguration(Cfg.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(FileStorageService.class);
                    assertThat(ctx).hasSingleBean(FileAdminService.class);
                    assertThat(ctx).hasSingleBean(FileRetentionJob.class);
                    FileModuleRegistry registry = ctx.getBean(FileModuleRegistry.class);
                    assertThat(registry.require("LIBRARY")).isInstanceOf(LibraryFileModule.class);
                });
    }
}