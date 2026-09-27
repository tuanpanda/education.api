package com.education.base.service.impl;

import com.education.base.dto.request.ClassCreateRequest;
import com.education.base.dto.request.ClassFilterRequest;
import com.education.base.dto.request.EnrollStudentsRequest;
import com.education.base.dto.response.ClassReportDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.CodeRuleEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.ClassMapperImpl;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.CodeRuleRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.service.FileStorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassServiceImplTest {

    @Mock
    private ClassRepository classRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private CodeRuleRepository codeRuleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private EntityManager entityManager;

    private ClassServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassServiceImpl(classRepository, classStudentRepository, studentRepository,
                codeRuleRepository, userRepository, fileStorageService, new ClassMapperImpl(),
                new FileMapperImpl(), entityManager);
    }

    @Test
    void search_delegatesToPagingProcedure() {
        ClassFilterRequest filter = ClassFilterRequest.builder().keyword("TOEIC").pageNo(1).pageSize(10).build();
        PageResponse<ClassReportDto> expected = PageResponse.of(List.of(), 1, 10, 0);
        when(classRepository.searchWithPaging(filter)).thenReturn(expected);

        assertThat(service.search(filter)).isSameAs(expected);
    }

    @Test
    void create_doesNotAssignClassCode_refreshesFromDatabase() {
        when(classRepository.saveAndFlush(any(ClassEntity.class))).thenAnswer(inv -> {
            ClassEntity e = inv.getArgument(0);
            assertThat(e.getClassCode()).isNull();
            e.setId(1L);
            return e;
        });
        doAnswer(inv -> {
            ClassEntity e = inv.getArgument(0);
            e.setClassCode("LH920260001");
            return null;
        }).when(entityManager).refresh(any(ClassEntity.class));
        when(classStudentRepository.findByClassIdAndIsDeleted(1L, 0)).thenReturn(List.of());
        when(fileStorageService.getFilesByRef("CLASS", 1L)).thenReturn(List.of());

        ClassCreateRequest request = new ClassCreateRequest();
        request.setClassName("TOEIC 450");
        request.setGradeLevel(9);

        var result = service.create(request);

        ArgumentCaptor<ClassEntity> captor = ArgumentCaptor.forClass(ClassEntity.class);
        verify(classRepository).saveAndFlush(captor.capture());
        verify(classRepository, never()).existsByClassCode(any());
        assertThat(captor.getValue().getClassName()).isEqualTo("TOEIC 450");
        assertThat(captor.getValue().getGradeLevel()).isEqualTo(9);
        assertThat(result.getClassCode()).isEqualTo("LH920260001");
        verify(entityManager).refresh(captor.getValue());
    }

    @Test
    void enroll_alreadyEnrolled_throws() {
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(5L).classCode("C01").className("TOEIC").capacity(20).tuitionAmount(BigDecimal.ZERO)
                .status("OPEN").isDeleted(0).build()));
        when(studentRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(StudentEntity.builder()
                .id(9L).studentCode("SV01").fullName("A").status("ACTIVE").isDeleted(0).build()));
        when(classStudentRepository.findByClassIdAndStudentId(5L, 9L))
                .thenReturn(Optional.of(ClassStudentEntity.builder()
                        .id(1L).classId(5L).studentId(9L).status("ENROLLED").isDeleted(0).build()));

        EnrollStudentsRequest request = EnrollStudentsRequest.builder()
                .classId(5L).studentIds(List.of(9L)).build();

        assertThatThrownBy(() -> service.enroll(5L, request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("STUDENT_ALREADY_ENROLLED");
    }

    @Test
    void enroll_studentInOtherClass_throws() {
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(5L).classCode("C01").className("TOEIC").capacity(20).tuitionAmount(BigDecimal.ZERO)
                .status("OPEN").isDeleted(0).build()));
        when(studentRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(StudentEntity.builder()
                .id(9L).studentCode("SV01").fullName("A").status("ACTIVE").isDeleted(0).build()));
        when(classStudentRepository.findByStudentIdAndIsDeleted(9L, 0))
                .thenReturn(List.of(ClassStudentEntity.builder()
                        .id(2L).classId(3L).studentId(9L).status("ENROLLED").isDeleted(0).build()));

        EnrollStudentsRequest request = EnrollStudentsRequest.builder()
                .classId(5L).studentIds(List.of(9L)).build();

        assertThatThrownBy(() -> service.enroll(5L, request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("STUDENT_ALREADY_IN_OTHER_CLASS");
    }

    @Test
    void unenroll_softDeletesEnrollment() {
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(5L).classCode("C01").className("TOEIC").status("OPEN").isDeleted(0).build()));
        ClassStudentEntity enrollment = ClassStudentEntity.builder()
                .id(11L).classId(5L).studentId(9L).status("ENROLLED").isDeleted(0).build();
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(5L, 9L, 0))
                .thenReturn(Optional.of(enrollment));

        service.unenroll(5L, 9L);

        assertThat(enrollment.getIsDeleted()).isEqualTo(1);
        verify(classStudentRepository).save(enrollment);
    }

    @Test
    void unenroll_missingEnrollment_throws() {
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(5L).classCode("C01").className("TOEIC").status("OPEN").isDeleted(0).build()));
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(5L, 9L, 0))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unenroll(5L, 9L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("ENROLLMENT_NOT_FOUND");
    }

    @Test
    void enroll_notOpen_throws() {
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(ClassEntity.builder()
                .id(5L).classCode("C01").className("TOEIC").status("ONGOING").isDeleted(0).build()));

        EnrollStudentsRequest request = EnrollStudentsRequest.builder()
                .classId(5L).studentIds(List.of(9L)).build();

        assertThatThrownBy(() -> service.enroll(5L, request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("CLASS_NOT_OPEN");
    }

    @Test
    void listOpenOptions_mapsSubjectNameToCourseName() {
        when(classRepository.findByStatusInAndIsDeletedOrderByCreatedAtDesc(
                List.of("OPEN"), 0))
                .thenReturn(List.of(ClassEntity.builder()
                        .id(1L)
                        .classCode("C01")
                        .className("TOEIC 450")
                        .gradeLevel(9)
                        .subjectName("TOEIC")
                        .status("OPEN")
                        .isDeleted(0)
                        .build()));

        var options = service.listOpenOptions();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getCourseName()).isEqualTo("TOEIC");
        assertThat(options.get(0).getClassCode()).isEqualTo("C01");
        assertThat(options.get(0).getGradeLevel()).isEqualTo(9);
        assertThat(options.get(0).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void peekNextClassCode_readsSysCodeRulesWithoutConsumingSeq() {
        when(codeRuleRepository.findByRuleCodeAndIsDeleted("CLASS", 0))
                .thenReturn(Optional.of(CodeRuleEntity.builder()
                        .ruleCode("CLASS")
                        .prefix("LH")
                        .pattern("{PREFIX}{GRADE}{YYYY}{SEQ}")
                        .seqLength(4)
                        .resetCycle("NEVER")
                        .lastResetKey("*")
                        .lastSeq(12L)
                        .isActive(1)
                        .isDeleted(0)
                        .build()));

        var preview = service.peekNextClassCode(9);

        assertThat(preview.isPreview()).isTrue();
        assertThat(preview.getGradeLevel()).isEqualTo(9);
        assertThat(preview.getClassCode()).startsWith("LH9");
        assertThat(preview.getClassCode()).endsWith("0013");
        verify(codeRuleRepository).findByRuleCodeAndIsDeleted("CLASS", 0);
    }
}
