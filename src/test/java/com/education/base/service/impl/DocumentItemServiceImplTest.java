package com.education.base.service.impl;

import com.education.base.common.document.DocErrorCodes;
import com.education.base.dto.request.DocItemCreateRequest;
import com.education.base.dto.request.DocItemFilterRequest;
import com.education.base.dto.request.DocItemUpdateRequest;
import com.education.base.dto.response.DocItemDto;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.entity.DocFolderEntity;
import com.education.base.entity.DocItemEntity;
import com.education.base.entity.FileEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.repository.DocItemRepository;
import com.education.base.repository.DocShareRepository;
import com.education.base.repository.UserRepository;
import com.education.base.security.Permissions;
import com.education.base.service.AuditService;
import com.education.base.service.DocTestData;
import com.education.base.service.DocumentAccessService;
import com.education.base.service.DocumentShareService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentItemServiceImplTest {

    @Mock
    private DocumentAccessService accessService;
    @Mock
    private DocumentShareService shareService;
    @Mock
    private DocItemRepository itemRepository;
    @Mock
    private DocShareRepository shareRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private FileLifecycleService fileLifecycleService;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private DocumentItemServiceImpl service;

    private final MockMultipartFile upload = new MockMultipartFile("file", "Đề cương.pdf", "application/pdf",
            new byte[]{1, 2, 3});
    private Map<Long, DocFolderEntity> folders;

    @BeforeEach
    void setUp() {
        folders = DocTestData.folders(DocTestData.folder(1, null, "/1/", 1L), DocTestData.folder(2, 1L, "/1/2/", 1L));
        lenient().when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(shareService.directItemShares(any())).thenReturn(Map.of());
        lenient().when(shareService.folderShares(any())).thenReturn(Map.of());
        lenient().when(shareService.describeItemShares(any(), any(), anyBoolean())).thenReturn(List.of());
        lenient().when(fileMapper.toDto(any())).thenAnswer(inv -> FileResponseDto.builder()
                .id(((FileEntity) inv.getArgument(0)).getId()).build());
    }

    private DocumentAccessService.Context teacherCtx(Set<Long> sharedFolders, Set<Long> sharedItems) {
        return DocTestData.ctx(DocTestData.staff(7L, Permissions.TEACHER_ROLE), false, true, false, false, false,
                Set.of(15L), folders, sharedFolders, sharedItems);
    }

    private static FileEntity file(long id) {
        return FileEntity.builder().id(id).originalName("Đề cương.pdf").moduleName("LIBRARY").build();
    }

    @Test
    void create_storesFileWithPreallocatedId_thenSavesItem() {
        when(accessService.current()).thenReturn(teacherCtx(Set.of(2L), Set.of()));
        when(itemRepository.nextId()).thenReturn(40L);
        when(fileStorageService.storeFile(upload, "LIBRARY", 40L)).thenReturn(file(220));
        DocItemDto dto = service.create(upload, DocItemCreateRequest.builder().folderId(2L)
                .tags(List.of("Toán", "toán", " Lop10 ")).build());
        assertThat(dto.getId()).isEqualTo(40L);
        assertThat(dto.getTitle()).isEqualTo("Đề cương");
        assertThat(dto.getTags()).containsExactly("toán", "lop10");
        assertThat(dto.getVersionNo()).isEqualTo(1);
        assertThat(dto.getOwnerUserId()).isEqualTo(7L);
        assertThat(dto.getFile().getId()).isEqualTo(220L);
        assertThat(dto.getPermissions().isCanEdit()).isTrue();
        verify(auditService).success(eq("DOC_ITEM_CREATED"), eq("DOC_ITEM"), eq(40L), any());
    }

    @Test
    void create_atRoot_requiresManage() {
        when(accessService.current()).thenReturn(teacherCtx(Set.of(2L), Set.of()));
        assertThatThrownBy(() -> service.create(upload, new DocItemCreateRequest()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FORBIDDEN);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void create_inHiddenFolder_is404() {
        when(accessService.current()).thenReturn(teacherCtx(Set.of(), Set.of()));
        assertThatThrownBy(() -> service.create(upload, DocItemCreateRequest.builder().folderId(2L).build()))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_NOT_FOUND);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void replaceFile_bumpsVersion_andTrashesOldFile() {
        DocItemEntity item = DocTestData.item(40, 2L, 7);
        when(accessService.current()).thenReturn(teacherCtx(Set.of(), Set.of()));
        when(itemRepository.findByIdAndIsDeleted(40L, 0)).thenReturn(Optional.of(item));
        when(fileStorageService.storeFile(upload, "LIBRARY", 40L)).thenReturn(file(221));
        DocItemDto dto = service.replaceFile(40L, upload, "Sửa chương 3");
        assertThat(dto.getVersionNo()).isEqualTo(2);
        assertThat(dto.getVersionNote()).isEqualTo("Sửa chương 3");
        assertThat(item.getFileId()).isEqualTo(221L);
        verify(fileLifecycleService).softDelete(140L, DocumentItemServiceImpl.REASON_REPLACED);
        verify(auditService).success(eq("DOC_ITEM_FILE_REPLACED"), eq("DOC_ITEM"), eq(40L), any());
    }

    @Test
    void sharedButNotOwned_edit403_hidden404() {
        DocItemEntity shared = DocTestData.item(41, 1L, 99);
        DocItemEntity hidden = DocTestData.item(42, 1L, 99);
        when(accessService.current()).thenReturn(teacherCtx(Set.of(), Set.of(41L)));
        when(itemRepository.findByIdAndIsDeleted(41L, 0)).thenReturn(Optional.of(shared));
        when(itemRepository.findByIdAndIsDeleted(42L, 0)).thenReturn(Optional.of(hidden));
        DocItemUpdateRequest req = DocItemUpdateRequest.builder().folderId(1L).title("x").build();
        assertThatThrownBy(() -> service.update(41L, req))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FORBIDDEN);
        assertThatThrownBy(() -> service.delete(41L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.replaceFile(41L, upload, null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.getItem(42L))
                .isInstanceOf(OracleBusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_ITEM_NOT_FOUND);
        assertThatThrownBy(() -> service.update(42L, req))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_ITEM_NOT_FOUND);
        verifyNoInteractions(fileStorageService, fileLifecycleService);
    }

    @Test
    void delete_softDeletes_removesShares_trashesFile() {
        DocItemEntity item = DocTestData.item(40, 2L, 7);
        when(accessService.current()).thenReturn(teacherCtx(Set.of(), Set.of()));
        when(itemRepository.findByIdAndIsDeleted(40L, 0)).thenReturn(Optional.of(item));
        service.delete(40L);
        assertThat(item.getIsDeleted()).isEqualTo(1);
        assertThat(item.getDeletedAt()).isNotNull();
        verify(shareRepository).deleteByItemId(40L);
        verify(fileLifecycleService).softDelete(140L, DocumentItemServiceImpl.REASON_DELETED);
    }

    @Test
    void move_toFolderWithoutCreateRight_rejected() {
        DocItemEntity item = DocTestData.item(40, 2L, 7);
        when(accessService.current()).thenReturn(teacherCtx(Set.of(2L), Set.of()));
        when(itemRepository.findByIdAndIsDeleted(40L, 0)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.update(40L, DocItemUpdateRequest.builder().folderId(null).title("t").build()))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FORBIDDEN);
        assertThat(item.getFolderId()).isEqualTo(2L);
        verify(itemRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_returnsPageWithPermissions() {
        DocItemEntity item = DocTestData.item(40, 2L, 7);
        when(accessService.current()).thenReturn(teacherCtx(Set.of(2L), Set.of()));
        Page<DocItemEntity> page = new PageImpl<>(List.of(item));
        when(itemRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        var res = service.search(DocItemFilterRequest.builder().folderId(2L).q("de").tag("Toan").build());
        assertThat(res.getTotalRows()).isEqualTo(1);
        assertThat(res.getContent().get(0).getPermissions().isCanDelete()).isTrue();
    }

    @Test
    void search_unknownFolder_is404_andClassWithoutShares_isEmpty() {
        when(accessService.current()).thenReturn(teacherCtx(Set.of(), Set.of()));
        assertThatThrownBy(() -> service.search(DocItemFilterRequest.builder().folderId(77L).build()))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_NOT_FOUND);
        when(shareRepository.findActiveClassShares(eq(15L), any())).thenReturn(List.of());
        var res = service.search(DocItemFilterRequest.builder().classId(15L).includeSubfolders(true).build());
        assertThat(res.getTotalRows()).isZero();
    }

    @Test
    void tagCodec() {
        assertThat(DocumentItemServiceImpl.encodeTags(List.of("A", "a", "b,c", " "))).isEqualTo(",a,b c,");
        assertThat(DocumentItemServiceImpl.encodeTags(List.of())).isNull();
        assertThat(DocumentItemServiceImpl.decodeTags(",a,b c,")).containsExactly("a", "b c");
        assertThat(DocumentItemServiceImpl.defaultTitle("bao-cao.final.xlsx")).isEqualTo("bao-cao.final");
        assertThat(DocumentItemServiceImpl.escapeLike("50%_x")).isEqualTo("50\\%\\_x");
    }
}