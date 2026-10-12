package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.config.FilesProperties;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.LeadRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.impl.TeachingAssignmentGuard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FileModuleRegistryTest {

    private final TeacherFileScope scope = new TeacherFileScope(mock(TeachingAssignmentGuard.class),
            mock(ClassStudentRepository.class), new FilesProperties());

    private final FileModuleRegistry registry = new FileModuleRegistry(List.of(
            new StudentFileModule(mock(StudentRepository.class), scope),
            new StudentImportFileModule(),
            new ClassFileModule(mock(ClassRepository.class), scope),
            new LeadFileModule(mock(LeadRepository.class)),
            new TuitionFileModule(mock(TuitionFeeRepository.class)),
            new CommonFileModule()));

    @Test
    void streamA_registersAllCoreModules() {
        assertThat(registry.all()).extracting(d -> d.code()).containsExactlyInAnyOrder(
                FileConstants.Module.STUDENT, FileConstants.Module.STUDENT_IMPORT, FileConstants.Module.CLASS,
                FileConstants.Module.LEAD, FileConstants.Module.TUITION, FileConstants.Module.COMMON);
    }

    @Test
    void unknownModules_areRejected() {
        for (String m : List.of("LIBRARY", "PAYROLL", "HOMEWORK")) {
            assertThat(registry.find(m)).isEmpty();
            assertThatThrownBy(() -> registry.require(m)).isInstanceOf(OracleBusinessException.class)
                    .extracting(e -> ((OracleBusinessException) e).getErrorCode()).isEqualTo("INVALID_MODULE");
        }
    }

    @Test
    void lookupIsCaseInsensitive_blankMeansCommon() {
        assertThat(registry.find(" class ")).isPresent();
        assertThat(registry.require(null).code()).isEqualTo("COMMON");
    }

    @Test
    void studentImportFiles_resolveToImportDescriptor() {
        FileEntity imp = FileEntity.builder().moduleName("STUDENT").filePath("STUDENT\\IMPORT\\2026\\09\\a.xlsx").build();
        FileEntity doc = FileEntity.builder().moduleName("STUDENT").filePath("STUDENT/2026/09/a.pdf").build();
        assertThat(registry.forFile(imp).orElseThrow().code()).isEqualTo("STUDENT_IMPORT");
        assertThat(registry.forFile(doc).orElseThrow().code()).isEqualTo("STUDENT");
        assertThat(FileModuleRegistry.descriptorCode("student", "import")).isEqualTo("STUDENT_IMPORT");
        assertThat(FileModuleRegistry.descriptorCode("CLASS", "IMPORT")).isEqualTo("CLASS");
    }

    @Test
    void genericUpload_flags() {
        assertThat(registry.require("STUDENT_IMPORT").allowGenericUpload()).isFalse();
        assertThat(registry.require("STUDENT").referenceRequired()).isTrue();
        assertThat(registry.require("COMMON").referenceRequired()).isFalse();
    }

    @Test
    void duplicateDescriptors_failFast() {
        assertThatThrownBy(() -> new FileModuleRegistry(List.of(new CommonFileModule(), new CommonFileModule())))
                .isInstanceOf(IllegalStateException.class);
    }
}
