package com.education.base.service.file;

import com.education.base.config.FileStorageProperties;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.entity.StudentEntity;
import com.education.base.config.FilesProperties;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.service.impl.TeachingAssignmentGuard;
import com.education.base.storage.BlobStore;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileLifecycleServiceImplTest {

    private FileRepository repo;
    private BlobStore blobStore;
    private StudentRepository studentRepository;
    private FileLifecycleServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repo = mock(FileRepository.class);
        blobStore = mock(BlobStore.class);
        studentRepository = mock(StudentRepository.class);
        when(repo.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));
        TeacherFileScope scope = new TeacherFileScope(mock(TeachingAssignmentGuard.class),
                mock(ClassStudentRepository.class), new FilesProperties());
        FileModuleRegistry registry = new FileModuleRegistry(List.of(new StudentFileModule(studentRepository, scope)));
        FileStorageProperties props = new FileStorageProperties();
        props.getRetention().setTrashDays(30);
        FileLimitService limits = new FileLimitService(props, mock(ObjectProvider.class), repo, blobStore, registry);
        service = new FileLifecycleServiceImpl(repo, blobStore, registry, limits);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void softDelete_movesActiveToTrash() {
        stub(file(1L, "ACTIVE", 0));
        FileEntity f = service.softDelete(1L, "  trùng file  ");

        assertThat(f.getStatus()).isEqualTo("DELETED");
        assertThat(f.getIsDeleted()).isEqualTo(1);
        assertThat(f.getDeleteReason()).isEqualTo("trùng file");
        assertThat(f.getDeletedBy()).isEqualTo("SYSTEM");
        assertThat(f.getPurgeAfter()).isCloseTo(LocalDateTime.now().plusDays(30), within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void softDelete_reasonTruncatedTo500() {
        stub(file(1L, "ACTIVE", 0));
        assertThat(service.softDelete(1L, "x".repeat(600)).getDeleteReason()).hasSize(500);
    }

    @Test
    void softDelete_nonActive_isInvalidState_missing_isNotFound() {
        stub(file(2L, "DELETED", 1));
        assertCode(() -> service.softDelete(2L, null), "FILE_INVALID_STATE");
        when(repo.findById(3L)).thenReturn(Optional.empty());
        assertCode(() -> service.softDelete(3L, null), "FILE_NOT_FOUND");
    }

    @Test
    void restore_requiresDeletedAndBlobAndActiveReference() {
        FileEntity f = file(4L, "DELETED", 1);
        f.setReferenceId(12L);
        f.setDeleteReason("x");
        stub(f);
        when(blobStore.exists(f.getFilePath())).thenReturn(false);
        assertCode(() -> service.restore(4L), "FILE_BLOB_MISSING");

        when(blobStore.exists(f.getFilePath())).thenReturn(true);
        when(studentRepository.findById(12L)).thenReturn(Optional.of(student(12L, 1)));
        assertCode(() -> service.restore(4L), "FILE_REFERENCE_INACTIVE");

        when(studentRepository.findById(12L)).thenReturn(Optional.of(student(12L, 0)));
        FileEntity r = service.restore(4L);
        assertThat(r.getStatus()).isEqualTo("ACTIVE");
        assertThat(r.getIsDeleted()).isZero();
        assertThat(r.getDeleteReason()).isNull();
        assertThat(r.getPurgeAfter()).isNull();

        stub(file(5L, "ACTIVE", 0));
        assertCode(() -> service.restore(5L), "FILE_INVALID_STATE");
    }

    @Test
    void purge_onlyTrash_deletesBlobAfterCommit_keepsRow() throws Exception {
        stub(file(6L, "ACTIVE", 0));
        assertCode(() -> service.purge(6L, null), "FILE_INVALID_STATE");

        FileEntity f = file(7L, "DELETED", 1);
        stub(f);
        TransactionSynchronizationManager.initSynchronization();
        FileEntity p = service.purge(7L, "het han");
        assertThat(p.getStatus()).isEqualTo("PURGED");
        assertThat(p.getPurgedAt()).isNotNull();
        verify(blobStore, never()).delete(any());
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        syncs.forEach(TransactionSynchronization::afterCommit);
        verify(blobStore).delete(f.getFilePath());
    }

    @Test
    void purge_withoutTransaction_deletesImmediately_andSwallowsBlobErrors() throws Exception {
        FileEntity f = file(8L, "PENDING", 0);
        stub(f);
        org.mockito.Mockito.doThrow(new java.io.IOException("locked")).when(blobStore).delete(f.getFilePath());
        assertThat(service.purge(8L, null).getStatus()).isEqualTo("PURGED");
        verify(blobStore).delete(f.getFilePath());
    }

    private void stub(FileEntity f) {
        when(repo.findById(f.getId())).thenReturn(Optional.of(f));
    }

    private static FileEntity file(Long id, String status, int deleted) {
        return FileEntity.builder().id(id).moduleName("STUDENT").filePath("STUDENT/2026/10/" + id + ".pdf")
                .status(status).isDeleted(deleted).build();
    }

    private static StudentEntity student(Long id, int deleted) {
        StudentEntity s = new StudentEntity();
        s.setId(id);
        s.setIsDeleted(deleted);
        return s;
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode()).isEqualTo(code);
    }
}
