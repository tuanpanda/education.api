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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
        stubSaveAllAssigningIds(10L);

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isZero();
        List<StudentEntity> saved = captureSavedStudents();
        assertThat(saved).hasSize(2).allMatch(e -> e.getStudentCode() == null);
        verify(studentRepository, never()).saveAndFlush(any());
        verify(studentRepository, never()).findExistingCodesUpper(anyCollection());
    }

    @Test
    void import_recordsCurrentUserAndStoresFileOnlyAfterSuccessfulCommit() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("tuyensinh01", null, List.of()));
        byte[] xlsx = buildWorkbookWithClass("HSNEW09", "Pham Van E", "EC920260001");
        MockMultipartFile file = new MockMultipartFile(
                "file", "lop.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(studentRepository.findExistingCodesUpper(anyCollection())).thenReturn(List.of());
        when(classRepository.findByClassCodesUpperAndIsDeleted(anyCollection(), anyInt()))
                .thenReturn(List.of(ClassEntity.builder().id(8L).classCode("EC920260001").status("OPEN").capacity(0).build()));
        stubSaveAllAssigningIds(30L);

        service.importStudents(file);

        List<StudentEntity> saved = captureSavedStudents();
        assertThat(saved).singleElement().extracting(StudentEntity::getCreatedBy).isEqualTo("tuyensinh01");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClassStudentEntity>> enrollments = ArgumentCaptor.forClass(List.class);
        verify(classStudentRepository).saveAll(enrollments.capture());
        assertThat(enrollments.getValue()).singleElement()
                .satisfies(e -> {
                    assertThat(e.getCreatedBy()).isEqualTo("tuyensinh01");
                    assertThat(e.getStudentId()).isEqualTo(30L);
                });

        // File audit được lưu SAU khi transaction commit.
        InOrder order = inOrder(transactionManager, fileStorageService);
        order.verify(transactionManager).commit(any());
        order.verify(fileStorageService).storeBytes(any(), eq("lop.xlsx"), any(), eq("STUDENT"), isNull(), eq("IMPORT"));
    }

    @Test
    void import_emptyWorkbook_doesNotStoreFile() {
        byte[] xlsx = buildWorkbookWithoutDataRows();
        MockMultipartFile file = new MockMultipartFile(
                "file", "rong.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);

        assertThatThrownBy(() -> service.importStudents(file))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("IMPORT_NO_DATA");
        verify(fileStorageService, never()).storeBytes(any(), any(), any(), any(), any(), any());
    }

    @Test
    void import_databaseFailure_rollsBackAndDoesNotStoreFile() {
        byte[] xlsx = buildWorkbookWithClass("HSNEW10", "Do Van F", "");
        MockMultipartFile file = new MockMultipartFile(
                "file", "loi.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(studentRepository.findExistingCodesUpper(anyCollection())).thenReturn(List.of());
        when(studentRepository.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("ORA-00001"));

        assertThatThrownBy(() -> service.importStudents(file))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(transactionManager).rollback(any());
        verify(fileStorageService, never()).storeBytes(any(), any(), any(), any(), any(), any());
    }

    @Test
    void import_fileStorageFailureAfterCommit_stillReturnsImportResult() {
        byte[] xlsx = buildWorkbookWithClass("HSNEW11", "Vu Thi G", "");
        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(studentRepository.findExistingCodesUpper(anyCollection())).thenReturn(List.of());
        stubSaveAllAssigningIds(40L);
        when(fileStorageService.storeBytes(any(), any(), any(), any(), any(), any()))
                .thenThrow(new OracleBusinessException("FILE_STORE_ERROR", "Không thể lưu file vào hệ thống."));

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    void import_flagsExistingDatabaseCode() {
        byte[] xlsx = buildWorkbookWithClass("HS20260001", "Nguyen Van C", "");
        MockMultipartFile file = new MockMultipartFile(
                "file", "mau.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(studentRepository.findExistingCodesUpper(anyCollection()))
                .thenReturn(List.of("HS20260001"));

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(err ->
                err.getStudentCode().equals("HS20260001")
                        && err.getErrorMessage().contains("đã tồn tại"));
        verify(studentRepository, never()).saveAll(any());
    }

    @Test
    void import_rejectsUnknownClass() {
        byte[] xlsx = buildWorkbookWithClass("HSNEW01", "Nguyen Van C", "MISSING");
        MockMultipartFile file = new MockMultipartFile(
                "file", "lop.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(studentRepository.findExistingCodesUpper(anyCollection())).thenReturn(List.of());
        when(classRepository.findByClassCodesUpperAndIsDeleted(anyCollection(), anyInt()))
                .thenReturn(List.of());

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getErrors()).anyMatch(err -> err.getColumnName().contains("lớp"));
        verify(studentRepository, never()).saveAll(any());
    }

    @Test
    void import_enrollsWhenClassOpen() {
        byte[] xlsx = buildWorkbookWithClass("HSNEW02", "Le Thi D", "EC920260001");
        MockMultipartFile file = new MockMultipartFile(
                "file", "lop.xlsx", StudentExcelHelper.XLSX_CONTENT_TYPE, xlsx);
        when(studentRepository.findExistingCodesUpper(anyCollection())).thenReturn(List.of());
        when(classRepository.findByClassCodesUpperAndIsDeleted(anyCollection(), anyInt()))
                .thenReturn(List.of(ClassEntity.builder()
                        .id(8L)
                        .classCode("EC920260001")
                        .status("OPEN")
                        .capacity(0)
                        .build()));
        stubSaveAllAssigningIds(20L);

        StudentImportResultResponse result = service.importStudents(file);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClassStudentEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(classStudentRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(e -> {
            assertThat(e.getClassId()).isEqualTo(8L);
            assertThat(e.getStudentId()).isEqualTo(20L);
        });
        verify(entityManager).flush();
    }

    private void stubSaveAllAssigningIds(long firstId) {
        when(studentRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<StudentEntity> list = inv.getArgument(0);
            long id = firstId;
            for (StudentEntity e : list) {
                e.setId(id++);
            }
            return list;
        });
    }

    private List<StudentEntity> captureSavedStudents() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StudentEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(studentRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private byte[] buildWorkbookWithoutDataRows() {
        byte[] template = excelHelper.generateStudentTemplate();
        try (var in = new java.io.ByteArrayInputStream(template);
             var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in);
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.getSheet(StudentExcelHelper.SHEET_DATA);
            for (int i = sheet.getLastRowNum(); i >= 1; i--) {
                var row = sheet.getRow(i);
                if (row != null) {
                    sheet.removeRow(row);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
