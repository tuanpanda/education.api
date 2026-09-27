package com.education.base.service.impl;

import com.education.base.common.excel.StudentExcelHelper;
import com.education.base.dto.response.StudentImportResultResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.service.FileStorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentImportServiceImplTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private EntityManager entityManager;

    private StudentImportServiceImpl service;
    private final StudentExcelHelper excelHelper = new StudentExcelHelper();

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new StudentImportServiceImpl(
                excelHelper, studentRepository, classRepository, classStudentRepository,
                fileStorageService, transactionManager, entityManager);
    }

    @Test
    void import_rejectsNonXlsx() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ds.xls", "application/vnd.ms-excel", new byte[]{1, 2});

        assertThatThrownBy(() -> service.importStudents(file))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("FILE_TYPE_INVALID");
        verify(fileStorageService, never()).storeBytes(any(), any(), any(), any(), any(), any());
    }

    @Test
    void import_blankStudentCode_letsTriggerGenerateLikeCreateForm() {
        byte[] xlsx = excelHelper.generateStudentTemplate();
        MockMultipartFile file = new MockMultipartFile(
                "file", "mau_import_hoc_sinh.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);

        when(fileStorageService.storeBytes(any(), any(), any(), eq("STUDENT"), isNull(), eq("IMPORT")))
                .thenReturn(FileEntity.builder().id(1L).build());
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isZero();
        ArgumentCaptor<StudentEntity> captor = ArgumentCaptor.forClass(StudentEntity.class);
        verify(studentRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).allMatch(e -> e.getStudentCode() == null);
        verify(studentRepository, never()).findExistingCodesUpper(anyCollection());
    }

    @Test
    void import_flagsExistingDatabaseCode() {
        byte[] xlsx = buildWorkbookWithClass("HS20260001", "Nguyen Van C", "");
        MockMultipartFile file = new MockMultipartFile(
                "file", "mau.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(fileStorageService.storeBytes(any(), any(), any(), anyString(), isNull(), anyString()))
                .thenReturn(FileEntity.builder().id(1L).build());
        when(studentRepository.findExistingCodesUpper(anyCollection()))
                .thenReturn(List.of("HS20260001"));

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(err ->
                err.getStudentCode().equals("HS20260001")
                        && err.getErrorMessage().contains("đã tồn tại"));
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void import_rejectsUnknownClass() {
        byte[] xlsx = buildWorkbookWithClass("HSNEW01", "Nguyen Van C", "MISSING");
        MockMultipartFile file = new MockMultipartFile(
                "file", "lop.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(fileStorageService.storeBytes(any(), any(), any(), anyString(), isNull(), anyString()))
                .thenReturn(FileEntity.builder().id(2L).build());
        when(studentRepository.findExistingCodesUpper(anyCollection())).thenReturn(List.of());
        when(classRepository.findByClassCodesUpperAndIsDeleted(anyCollection(), anyInt()))
                .thenReturn(List.of());

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getErrors()).anyMatch(err -> err.getColumnName().contains("lớp"));
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void import_enrollsWhenClassOpen() {
        byte[] xlsx = buildWorkbookWithClass("HSNEW02", "Le Thi D", "EC920260001");
        MockMultipartFile file = new MockMultipartFile(
                "file", "lop.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(fileStorageService.storeBytes(any(), any(), any(), anyString(), isNull(), anyString()))
                .thenReturn(FileEntity.builder().id(3L).build());
        when(studentRepository.findExistingCodesUpper(anyCollection())).thenReturn(List.of());
        when(classRepository.findByClassCodesUpperAndIsDeleted(anyCollection(), anyInt()))
                .thenReturn(List.of(ClassEntity.builder()
                        .id(8L)
                        .classCode("EC920260001")
                        .status("OPEN")
                        .capacity(0)
                        .build()));
        when(studentRepository.saveAndFlush(any(StudentEntity.class))).thenAnswer(inv -> {
            StudentEntity e = inv.getArgument(0);
            e.setId(20L);
            return e;
        });

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<ClassStudentEntity> captor = ArgumentCaptor.forClass(ClassStudentEntity.class);
        verify(classStudentRepository).save(captor.capture());
        assertThat(captor.getValue().getClassId()).isEqualTo(8L);
        assertThat(captor.getValue().getStudentId()).isEqualTo(20L);
    }

    private byte[] buildWorkbookWithClass(String code, String name, String classCode) {
        byte[] template = excelHelper.generateStudentTemplate();
        try (var in = new java.io.ByteArrayInputStream(template);
             var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in);
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.getSheet(StudentExcelHelper.SHEET_DATA);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(code);
            row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(classCode);
            row.createCell(3).setCellValue("ACTIVE");
            sheet.createRow(2);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
