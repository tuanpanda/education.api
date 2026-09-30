package com.education.base.service.impl;

import com.education.base.dto.request.QuickCreateClassRequest;
import com.education.base.dto.request.StudentCreateRequest;
import com.education.base.dto.request.StudentFilterRequest;
import com.education.base.dto.request.StudentUpdateRequest;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.dto.response.StudentReportDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.mapper.StudentMapperImpl;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.service.FileStorageService;
import com.education.base.support.TestSecurityContexts;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EntityManager entityManager;

    private StudentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentServiceImpl(
                studentRepository, classRepository, classStudentRepository, attendanceRepository, userRepository,
                fileStorageService, new StudentMapperImpl(), new FileMapperImpl(),
                entityManager);
        TestSecurityContexts.loginAdmin(1L);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    private StudentEntity activeStudent(long id, String code) {
        return StudentEntity.builder()
                .id(id)
                .studentCode(code)
                .fullName("Nguyen Van A")
                .email("a@edu.com")
                .status("ACTIVE")
                .isDeleted(0)
                .build();
    }

    @Test
    void search_delegatesToProcedureBackedRepository() {
        StudentFilterRequest filter = StudentFilterRequest.builder().keyword("SV").pageNo(1).pageSize(10).build();
        PageResponse<StudentReportDto> expected = PageResponse.of(List.of(), 1, 10, 0);
        when(studentRepository.searchWithPaging(filter)).thenReturn(expected);

        assertThat(service.search(filter)).isSameAs(expected);
        verify(studentRepository).searchWithPaging(filter);
    }

    @Test
    void create_setsNotDeletedAndDefaultStatusWithoutAssigningStudentCode() {
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            assertThat(e.getStudentCode()).isNull();
            e.setId(10L);
            return e;
        });
        doAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setStudentCode("SV20260001");
            return null;
        }).when(entityManager).refresh(any(StudentEntity.class));

        StudentDetailResponse result = service.create(
                new StudentCreateRequest("Pham Van D", "d@edu.com", null));

        ArgumentCaptor<StudentEntity> captor = ArgumentCaptor.forClass(StudentEntity.class);
        verify(studentRepository).saveAndFlush(captor.capture());
        StudentEntity saved = captor.getValue();

        assertThat(saved.getIsDeleted()).isZero();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStudentCode()).isEqualTo("SV20260001");
        assertThat(result.getAttachments()).isEmpty();
        verify(studentRepository, never()).existsByStudentCode(any());
        verify(entityManager).refresh(saved);
        verify(classStudentRepository, never()).save(any());
    }

    @Test
    void create_mapsProfileFieldsFromRequest() {
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setId(11L);
            return e;
        });
        doAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setStudentCode("SV20260011");
            return null;
        }).when(entityManager).refresh(any(StudentEntity.class));

        StudentCreateRequest request = StudentCreateRequest.builder()
                .fullName("Nguyen Van An")
                .dateOfBirth(java.time.LocalDate.of(2012, 4, 26))
                .parentName("Mẹ")
                .phone("0797958563")
                .address("Hòa Bình Hạ")
                .note("Ghi chu")
                .status("ACTIVE")
                .build();

        StudentDetailResponse result = service.create(request);

        ArgumentCaptor<StudentEntity> captor = ArgumentCaptor.forClass(StudentEntity.class);
        verify(studentRepository).saveAndFlush(captor.capture());
        StudentEntity saved = captor.getValue();
        assertThat(saved.getDateOfBirth()).isEqualTo(java.time.LocalDate.of(2012, 4, 26));
        assertThat(saved.getParentName()).isEqualTo("Mẹ");
        assertThat(saved.getPhone()).isEqualTo("0797958563");
        assertThat(saved.getAddress()).isEqualTo("Hòa Bình Hạ");
        assertThat(saved.getNote()).isEqualTo("Ghi chu");
        assertThat(result.getDateOfBirth()).isEqualTo(java.time.LocalDate.of(2012, 4, 26));
        assertThat(result.getParentName()).isEqualTo("Mẹ");
        assertThat(result.getPhone()).isEqualTo("0797958563");
        assertThat(result.getAddress()).isEqualTo("Hòa Bình Hạ");
        assertThat(result.getNote()).isEqualTo("Ghi chu");
    }

    @Test
    void create_keepsExplicitStatus() {
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new StudentCreateRequest("Le Thi C", null, "GRADUATED"));

        ArgumentCaptor<StudentEntity> captor = ArgumentCaptor.forClass(StudentEntity.class);
        verify(studentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("GRADUATED");
        assertThat(captor.getValue().getStudentCode()).isNull();
    }

    @Test
    void getDetail_attachesFilesFromStudentModule() {
        when(studentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(activeStudent(1L, "SV001")));
        when(fileStorageService.getFilesByRef("STUDENT", 1L)).thenReturn(List.of(FileEntity.builder()
                .id(4L)
                .originalName("bang-diem.pdf")
                .contentType("application/pdf")
                .fileSize(120L)
                .moduleName("STUDENT")
                .referenceId(1L)
                .build()));

        StudentDetailResponse detail = service.getDetail(1L);

        assertThat(detail.getStudentCode()).isEqualTo("SV001");
        assertThat(detail.getAttachments()).hasSize(1);
        assertThat(detail.getAttendedSessionCount()).isZero();
        assertThat(detail.getAttendanceMarkedCount()).isZero();
        assertThat(detail.getAttendanceSessions()).isEmpty();
        FileResponseDto attachment = detail.getAttachments().get(0);
        assertThat(attachment.getViewUrl()).isEqualTo("/api/v1/files/view/4");
        assertThat(attachment.getDownloadUrl()).isEqualTo("/api/v1/files/download/4");
    }

    @Test
    void getDetail_includesAttendanceCounts() {
        when(studentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(activeStudent(1L, "SV001")));
        when(fileStorageService.getFilesByRef("STUDENT", 1L)).thenReturn(List.of());
        when(attendanceRepository.findByStudentIdAndIsDeleted(1L, 0)).thenReturn(List.of(
                AttendanceEntity.builder()
                        .id(11L)
                        .classId(5L)
                        .studentId(1L)
                        .attendanceDate(LocalDate.of(2026, 9, 21))
                        .status("PRESENT")
                        .build(),
                AttendanceEntity.builder()
                        .id(12L)
                        .classId(5L)
                        .studentId(1L)
                        .attendanceDate(LocalDate.of(2026, 9, 14))
                        .status("ABSENT")
                        .build(),
                AttendanceEntity.builder()
                        .id(13L)
                        .classId(8L)
                        .studentId(1L)
                        .attendanceDate(LocalDate.of(2026, 8, 10))
                        .status("LATE")
                        .build()));
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(
                ClassEntity.builder().id(5L).classCode("EC5").className("Lop 5").isDeleted(0).build()));
        when(classRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(
                ClassEntity.builder().id(8L).classCode("EC8").className("Lop 8").isDeleted(0).build()));

        StudentDetailResponse detail = service.getDetail(1L);

        assertThat(detail.getAttendedSessionCount()).isEqualTo(2L);
        assertThat(detail.getAttendanceMarkedCount()).isEqualTo(3L);
        assertThat(detail.getAttendanceSessions()).hasSize(3);
        assertThat(detail.getAttendanceSessions().get(0).getAttendanceDate())
                .isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(detail.getAttendanceSessions().get(0).getClassCode()).isEqualTo("EC5");
        assertThat(detail.getAttendanceSessions().get(2).getClassName()).isEqualTo("Lop 8");
    }

    @Test
    void getDetail_hidesNonActiveStudent() {
        when(studentRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(
                StudentEntity.builder().id(1L).studentCode("SV001").status("INACTIVE").isDeleted(0).build()));

        assertThatThrownBy(() -> service.getDetail(1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("STUDENT_NOT_FOUND");
    }

    @Test
    void getDetail_softDeletedOrMissing_throwsNotFound() {
        when(studentRepository.findByIdAndIsDeleted(99L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(99L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("STUDENT_NOT_FOUND");
    }

    @Test
    void getDetail_nullId_throwsBusinessException() {
        assertThatThrownBy(() -> service.getDetail(null))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("STUDENT_ID_REQUIRED");
    }

    @Test
    void update_doesNotChangeStudentCodeOrDeletedFlag() {
        StudentEntity existing = activeStudent(3L, "SV003");
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(StudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.getFilesByRef("STUDENT", 3L)).thenReturn(List.of());

        StudentDetailResponse result = service.update(3L,
                new StudentUpdateRequest("Ten Da Sua", "moi@edu.com", "INACTIVE"));

        assertThat(existing.getStudentCode()).isEqualTo("SV003");
        assertThat(existing.getIsDeleted()).isZero();
        assertThat(result.getFullName()).isEqualTo("Ten Da Sua");
        assertThat(result.getEmail()).isEqualTo("moi@edu.com");
        assertThat(result.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void update_mapsProfileFields() {
        StudentEntity existing = activeStudent(3L, "SV003");
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(StudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.getFilesByRef("STUDENT", 3L)).thenReturn(List.of());

        StudentUpdateRequest request = StudentUpdateRequest.builder()
                .fullName("Ten Da Sua")
                .email("moi@edu.com")
                .status("INACTIVE")
                .dateOfBirth(java.time.LocalDate.of(2012, 2, 22))
                .parentName("Mẹ")
                .phone("0867926431")
                .address("Đa Nguu")
                .note("")
                .build();

        StudentDetailResponse result = service.update(3L, request);

        assertThat(existing.getDateOfBirth()).isEqualTo(java.time.LocalDate.of(2012, 2, 22));
        assertThat(existing.getParentName()).isEqualTo("Mẹ");
        assertThat(existing.getPhone()).isEqualTo("0867926431");
        assertThat(existing.getAddress()).isEqualTo("Đa Nguu");
        assertThat(result.getPhone()).isEqualTo("0867926431");
    }

    @Test
    void update_withClassId_enrollsStudent() {
        StudentEntity existing = activeStudent(3L, "SV003");
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(StudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.getFilesByRef("STUDENT", 3L)).thenReturn(List.of());

        ClassEntity clazz = ClassEntity.builder()
                .id(5L)
                .classCode("C01")
                .className("TOEIC 450")
                .status("OPEN")
                .capacity(30)
                .isDeleted(0)
                .build();
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(clazz));
        when(classStudentRepository.existsByClassIdAndStudentId(5L, 3L)).thenReturn(false);
        when(classStudentRepository.countByClassIdAndStatusAndIsDeleted(5L, "ENROLLED", 0)).thenReturn(0L);
        when(classStudentRepository.save(any(ClassStudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentUpdateRequest request = StudentUpdateRequest.builder()
                .fullName("Ten Da Sua")
                .email("moi@edu.com")
                .status("ACTIVE")
                .classId(5L)
                .build();

        StudentDetailResponse result = service.update(3L, request);

        assertThat(result.getClassId()).isEqualTo(5L);
        assertThat(result.getClassCode()).isEqualTo("C01");
        verify(classStudentRepository).save(any(ClassStudentEntity.class));
    }

    @Test
    void update_withClassId_leavesPreviousClass() {
        StudentEntity existing = activeStudent(3L, "SV003");
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(StudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.getFilesByRef("STUDENT", 3L)).thenReturn(List.of());

        ClassStudentEntity previous = ClassStudentEntity.builder()
                .id(40L)
                .classId(2L)
                .studentId(3L)
                .status("ENROLLED")
                .isDeleted(0)
                .build();
        ClassEntity nextClass = ClassEntity.builder()
                .id(5L)
                .classCode("C01")
                .className("TOEIC 450")
                .status("OPEN")
                .capacity(30)
                .isDeleted(0)
                .build();
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(nextClass));
        when(classStudentRepository.findByStudentIdAndIsDeleted(3L, 0)).thenReturn(List.of(previous));
        when(classStudentRepository.findByClassIdAndStudentId(5L, 3L)).thenReturn(Optional.empty());
        when(classStudentRepository.existsByClassIdAndStudentId(5L, 3L)).thenReturn(false);
        when(classStudentRepository.countByClassIdAndStatusAndIsDeleted(5L, "ENROLLED", 0)).thenReturn(0L);
        when(classStudentRepository.save(any(ClassStudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentUpdateRequest request = StudentUpdateRequest.builder()
                .fullName("Ten Da Sua")
                .email("moi@edu.com")
                .status("ACTIVE")
                .classId(5L)
                .build();

        service.update(3L, request);

        assertThat(previous.getIsDeleted()).isEqualTo(1);
        verify(classStudentRepository, org.mockito.Mockito.atLeastOnce()).save(previous);
    }

    @Test
    void softDelete_setsIsDeletedToOne() {
        StudentEntity existing = activeStudent(4L, "SV004");
        when(studentRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.of(existing));

        service.softDelete(4L);

        ArgumentCaptor<StudentEntity> captor = ArgumentCaptor.forClass(StudentEntity.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getIsDeleted()).isEqualTo(1);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void softDelete_alreadyDeleted_throwsNotFound() {
        when(studentRepository.findByIdAndIsDeleted(4L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(4L))
                .isInstanceOf(OracleBusinessException.class);
        verify(studentRepository, never()).save(any(StudentEntity.class));
    }

    @Test
    void uploadDocument_storesUnderStudentModuleWithStudentIdAsReference() {
        StudentEntity existing = activeStudent(6L, "SV006");
        when(studentRepository.findByIdAndIsDeleted(6L, 0)).thenReturn(Optional.of(existing));

        MultipartFile file = new MockMultipartFile("file", "ho-so.pdf", "application/pdf", "x".getBytes());
        when(fileStorageService.storeFile(file, "STUDENT", 6L)).thenReturn(FileEntity.builder()
                .id(30L)
                .originalName("ho-so.pdf")
                .filePath("STUDENT/2026/09/uuid_ho-so.pdf")
                .moduleName("STUDENT")
                .referenceId(6L)
                .build());

        FileResponseDto dto = service.uploadDocument(6L, file);

        verify(fileStorageService).storeFile(file, "STUDENT", 6L);
        assertThat(dto.getId()).isEqualTo(30L);
        assertThat(dto.getModuleName()).isEqualTo("STUDENT");
        assertThat(dto.getReferenceId()).isEqualTo(6L);
        assertThat(dto.getViewUrl()).isEqualTo("/api/v1/files/view/30");
    }

    @Test
    void uploadDocument_studentNotFound_doesNotTouchStorage() {
        when(studentRepository.findByIdAndIsDeleted(99L, 0)).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes());

        assertThatThrownBy(() -> service.uploadDocument(99L, file))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("STUDENT_NOT_FOUND");

        verify(fileStorageService, never()).storeFile(any(), any(), any());
    }

    @Test
    void create_bothClassIdAndNewClass_throwsWithoutSaving() {
        StudentCreateRequest request = new StudentCreateRequest("Pham Van D", "d@edu.com", "ACTIVE");
        request.setClassId(5L);
        request.setNewClass(QuickCreateClassRequest.builder()
                .className("TOEIC 450")
                .gradeLevel(9)
                .build());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("CLASS_ASSIGNMENT_CONFLICT");
        verify(studentRepository, never()).saveAndFlush(any());
        verify(classRepository, never()).save(any());
    }

    @Test
    void create_withExistingClassId_enrollsStudent() {
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        doAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setStudentCode("SV20260002");
            return null;
        }).when(entityManager).refresh(any(StudentEntity.class));

        ClassEntity clazz = ClassEntity.builder()
                .id(5L)
                .classCode("C01")
                .className("TOEIC 450")
                .status("OPEN")
                .capacity(30)
                .isDeleted(0)
                .build();
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(clazz));
        when(classStudentRepository.existsByClassIdAndStudentId(5L, 10L)).thenReturn(false);
        when(classStudentRepository.countByClassIdAndStatusAndIsDeleted(5L, "ENROLLED", 0)).thenReturn(0L);
        when(classStudentRepository.save(any(ClassStudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentCreateRequest request = new StudentCreateRequest("Pham Van D", "d@edu.com", "ACTIVE");
        request.setClassId(5L);

        StudentDetailResponse result = service.create(request);

        assertThat(result.getClassId()).isEqualTo(5L);
        assertThat(result.getClassCode()).isEqualTo("C01");
        assertThat(result.getClassName()).isEqualTo("TOEIC 450");
        verify(classStudentRepository).save(any(ClassStudentEntity.class));
        verify(classRepository, never()).save(any());
    }

    @Test
    void create_missingClass_throws404() {
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(classRepository.findByIdAndIsDeleted(99L, 0)).thenReturn(Optional.empty());

        StudentCreateRequest request = new StudentCreateRequest("Pham Van D", "d@edu.com", "ACTIVE");
        request.setClassId(99L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("404");
        verify(classStudentRepository, never()).save(any());
    }

    @Test
    void create_withNewClass_createsOpenClassAndEnrolls() {
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        doAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (arg instanceof StudentEntity student) {
                student.setStudentCode("SV20260003");
            }
            if (arg instanceof ClassEntity clazz) {
                clazz.setClassCode("LH920260001");
            }
            return null;
        }).when(entityManager).refresh(any());
        when(classRepository.saveAndFlush(any(ClassEntity.class))).thenAnswer(inv -> {
            ClassEntity c = inv.getArgument(0);
            assertThat(c.getClassCode()).isNull();
            c.setId(8L);
            return c;
        });
        when(classStudentRepository.existsByClassIdAndStudentId(8L, 10L)).thenReturn(false);
        when(classStudentRepository.countByClassIdAndStatusAndIsDeleted(8L, "ENROLLED", 0)).thenReturn(0L);
        when(classStudentRepository.save(any(ClassStudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentCreateRequest request = new StudentCreateRequest("Pham Van D", "d@edu.com", "ACTIVE");
        request.setNewClass(QuickCreateClassRequest.builder()
                .className("TOEIC 450")
                .gradeLevel(9)
                .courseName("TOEIC")
                .roomName("A101")
                .build());

        StudentDetailResponse result = service.create(request);

        ArgumentCaptor<ClassEntity> classCaptor = ArgumentCaptor.forClass(ClassEntity.class);
        verify(classRepository).saveAndFlush(classCaptor.capture());
        assertThat(classCaptor.getValue().getStatus()).isEqualTo("OPEN");
        assertThat(classCaptor.getValue().getSubjectName()).isEqualTo("TOEIC");
        assertThat(classCaptor.getValue().getGradeLevel()).isEqualTo(9);
        assertThat(result.getClassId()).isEqualTo(8L);
        assertThat(result.getClassCode()).isEqualTo("LH920260001");
        verify(classStudentRepository).save(any(ClassStudentEntity.class));
    }

    // ---- M6: tạo lớp / chuyển lớp qua API học sinh cần quyền trên lớp -------------------------------------

    private static QuickCreateClassRequest quickClass() {
        return QuickCreateClassRequest.builder().className("TOEIC 450").gradeLevel(9).build();
    }

    private static ClassEntity openClass(long id) {
        return ClassEntity.builder().id(id).classCode("C" + id).className("Lớp " + id)
                .status("OPEN").capacity(30).isDeleted(0).build();
    }

    private static StudentUpdateRequest moveTo(Long classId) {
        return StudentUpdateRequest.builder().fullName("Ten Da Sua").email("moi@edu.com").status("ACTIVE")
                .classId(classId).build();
    }

    @Test
    void create_withNewClass_withoutClassCreate_isForbidden() {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"), Set.of("MENU_STUDENT_LIST:CREATE"));
        StudentCreateRequest request = new StudentCreateRequest("Pham Van D", "d@edu.com", "ACTIVE");
        request.setNewClass(quickClass());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("tạo lớp");
        verify(studentRepository, never()).saveAndFlush(any());
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_withNewClass_withClassCreate_createsClass() {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"),
                Set.of("MENU_STUDENT_LIST:CREATE", "MENU_CLASS_LIST:CREATE"));
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(classRepository.saveAndFlush(any(ClassEntity.class))).thenAnswer(inv -> {
            ClassEntity c = inv.getArgument(0);
            c.setId(8L);
            return c;
        });
        when(classStudentRepository.save(any(ClassStudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        StudentCreateRequest request = new StudentCreateRequest("Pham Van D", "d@edu.com", "ACTIVE");
        request.setNewClass(quickClass());

        StudentDetailResponse result = service.create(request);

        assertThat(result.getClassId()).isEqualTo(8L);
        verify(classRepository).saveAndFlush(any(ClassEntity.class));
    }

    @Test
    void create_withExistingClass_doesNotRequireClassCreate() {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"), Set.of("MENU_STUDENT_LIST:CREATE"));
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(openClass(5L)));
        when(classStudentRepository.save(any(ClassStudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        StudentCreateRequest request = new StudentCreateRequest("Pham Van D", "d@edu.com", "ACTIVE");
        request.setClassId(5L);

        assertThat(service.create(request).getClassId()).isEqualTo(5L);
    }

    @Test
    void update_changingClass_withoutClassUpdate_isForbidden() {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"), Set.of("MENU_STUDENT_LIST:UPDATE"));
        StudentEntity existing = activeStudent(3L, "SV003");
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(existing));
        when(classStudentRepository.findByStudentIdAndIsDeleted(3L, 0)).thenReturn(List.of(ClassStudentEntity.builder()
                .id(40L).classId(2L).studentId(3L).status("ENROLLED").isDeleted(0).build()));

        assertThatThrownBy(() -> service.update(3L, moveTo(5L)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("chuyển lớp");
        assertThat(existing.getFullName()).isEqualTo("Nguyen Van A");
        verify(studentRepository, never()).save(any());
        verify(classStudentRepository, never()).save(any());
    }

    @Test
    void update_firstEnrollment_withoutClassUpdate_isForbidden() {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"), Set.of("MENU_STUDENT_LIST:UPDATE"));
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(activeStudent(3L, "SV003")));

        assertThatThrownBy(() -> service.update(3L, moveTo(5L)))
                .isInstanceOf(ForbiddenException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void update_sameClass_withoutClassUpdate_isAllowed() {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"), Set.of("MENU_STUDENT_LIST:UPDATE"));
        StudentEntity existing = activeStudent(3L, "SV003");
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(StudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ClassStudentEntity current = ClassStudentEntity.builder()
                .id(40L).classId(5L).studentId(3L).status("ENROLLED").isDeleted(0).build();
        when(classStudentRepository.findByStudentIdAndIsDeleted(3L, 0)).thenReturn(List.of(current));
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(openClass(5L)));
        when(classStudentRepository.findByClassIdAndStudentId(5L, 3L)).thenReturn(Optional.of(current));

        StudentDetailResponse result = service.update(3L, moveTo(5L));

        assertThat(result.getClassId()).isEqualTo(5L);
        assertThat(existing.getFullName()).isEqualTo("Ten Da Sua");
        verify(classStudentRepository, never()).save(any());
    }

    @Test
    void update_withoutClassId_doesNotRequireClassUpdate() {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"), Set.of("MENU_STUDENT_LIST:UPDATE"));
        StudentEntity existing = activeStudent(3L, "SV003");
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(existing));
        when(studentRepository.save(any(StudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(3L, moveTo(null));

        assertThat(existing.getFullName()).isEqualTo("Ten Da Sua");
        verify(classStudentRepository, never()).findByStudentIdAndIsDeleted(any(), any());
    }

    @Test
    void update_changingClass_withClassUpdate_transfers() {
        TestSecurityContexts.login(5L, List.of("ROLE_STAFF"),
                Set.of("MENU_STUDENT_LIST:UPDATE", "MENU_CLASS_LIST:UPDATE"));
        when(studentRepository.findByIdAndIsDeleted(3L, 0)).thenReturn(Optional.of(activeStudent(3L, "SV003")));
        when(studentRepository.save(any(StudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ClassStudentEntity previous = ClassStudentEntity.builder()
                .id(40L).classId(2L).studentId(3L).status("ENROLLED").isDeleted(0).build();
        when(classStudentRepository.findByStudentIdAndIsDeleted(3L, 0)).thenReturn(List.of(previous));
        when(classRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(openClass(5L)));
        when(classStudentRepository.save(any(ClassStudentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentDetailResponse result = service.update(3L, moveTo(5L));

        assertThat(previous.getIsDeleted()).isEqualTo(1);
        assertThat(result.getClassId()).isEqualTo(5L);
    }
}
