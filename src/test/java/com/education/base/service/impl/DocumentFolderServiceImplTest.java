package com.education.base.service.impl;

import com.education.base.common.document.DocErrorCodes;
import com.education.base.dto.request.DocFolderUpsertRequest;
import com.education.base.dto.response.DocFolderDto;
import com.education.base.dto.response.DocFolderTreeNodeDto;
import com.education.base.entity.DocFolderEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.DocFolderRepository;
import com.education.base.repository.DocItemRepository;
import com.education.base.repository.DocShareRepository;
import com.education.base.security.Permissions;
import com.education.base.service.AuditService;
import com.education.base.service.DocTestData;
import com.education.base.service.DocumentAccessService;
import com.education.base.service.DocumentShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentFolderServiceImplTest {

    @Mock
    private DocumentAccessService accessService;
    @Mock
    private DocumentShareService shareService;
    @Mock
    private DocFolderRepository folderRepository;
    @Mock
    private DocItemRepository itemRepository;
    @Mock
    private DocShareRepository shareRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private DocumentFolderServiceImpl service;

    private DocFolderEntity f1;
    private DocFolderEntity f2;
    private DocFolderEntity f3;
    private DocFolderEntity f5;
    private Map<Long, DocFolderEntity> folders;

    @BeforeEach
    void setUp() {
        f1 = DocTestData.folder(1, null, "/1/", 1L);
        f2 = DocTestData.folder(2, 1L, "/1/2/", 1L);
        f3 = DocTestData.folder(3, 2L, "/1/2/3/", 1L);
        f5 = DocTestData.folder(5, null, "/5/", 1L);
        folders = DocTestData.folders(f1, f2, f3, f5);
        lenient().when(folderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(itemRepository.countByFolder()).thenReturn(List.of());
        lenient().when(shareService.describeFolderShares(any(), any(), anyBoolean())).thenReturn(List.of());
    }

    private DocumentAccessService.Context manageCtx() {
        return DocTestData.ctx(DocTestData.admin(), true, true, true, true, true, Set.of(), folders, Set.of(),
                Set.of());
    }

    @Test
    void create_buildsTreePathAndDepth() {
        when(accessService.current()).thenReturn(manageCtx());
        when(folderRepository.nextId()).thenReturn(10L);
        when(folderRepository.countSiblingName(eq(2L), eq("Moi"), isNull())).thenReturn(0L);
        DocFolderDto dto = service.create(DocFolderUpsertRequest.builder().parentId(2L).name(" Moi ").build());
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(folders.get(10L).getTreePath()).isEqualTo("/1/2/10/");
        assertThat(folders.get(10L).getDepth()).isEqualTo(3);
        assertThat(dto.getBreadcrumbs()).extracting("id").containsExactly(1L, 2L, 10L);
        verify(auditService).success(eq("DOC_FOLDER_CREATED"), eq("DOC_FOLDER"), eq(10L), any());
    }

    @Test
    void create_duplicateSiblingName_rejected() {
        when(accessService.current()).thenReturn(manageCtx());
        when(folderRepository.countSiblingName(eq(0L), eq("F1"), isNull())).thenReturn(1L);
        assertThatThrownBy(() -> service.create(DocFolderUpsertRequest.builder().name("F1").build()))
                .isInstanceOf(OracleBusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_NAME_DUPLICATE);
    }

    @Test
    void create_tooDeep_rejected() {
        DocFolderEntity deep = DocTestData.folder(8, 7L, "/1/2/3/4/5/6/7/8/", 1L);
        folders.put(8L, deep);
        when(accessService.current()).thenReturn(manageCtx());
        assertThatThrownBy(() -> service.create(DocFolderUpsertRequest.builder().parentId(8L).name("x").build()))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_TOO_DEEP);
    }

    @Test
    void create_withoutManage_forbidden() {
        when(accessService.current()).thenReturn(DocTestData.ctx(
                DocTestData.staff(7L, Permissions.TEACHER_ROLE), false, true, false, false, false, Set.of(),
                folders, Set.of(), Set.of()));
        assertThatThrownBy(() -> service.create(DocFolderUpsertRequest.builder().name("x").build()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FORBIDDEN);
    }

    @Test
    void move_intoOwnDescendant_isCycle() {
        when(accessService.current()).thenReturn(manageCtx());
        assertThatThrownBy(() -> service.update(1L, DocFolderUpsertRequest.builder().parentId(3L).name("F1").build()))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_CYCLE);
        assertThatThrownBy(() -> service.update(1L, DocFolderUpsertRequest.builder().parentId(1L).name("F1").build()))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_CYCLE);
    }

    @Test
    void move_updatesWholeSubtree() {
        when(accessService.current()).thenReturn(manageCtx());
        when(folderRepository.findSubtree("/1/2/")).thenReturn(new ArrayList<>(List.of(f2, f3)));
        service.update(2L, DocFolderUpsertRequest.builder().parentId(5L).name("F2").build());
        assertThat(f2.getTreePath()).isEqualTo("/5/2/");
        assertThat(f2.getParentId()).isEqualTo(5L);
        assertThat(f3.getTreePath()).isEqualTo("/5/2/3/");
        assertThat(f3.getDepth()).isEqualTo(3);
        verify(auditService).success(eq("DOC_FOLDER_MOVED"), eq("DOC_FOLDER"), eq(2L), any());
    }

    @Test
    void move_subtreeExceedingDepth_rejected() {
        DocFolderEntity deep = DocTestData.folder(9, 15L, "/5/11/12/13/14/15/9/", 1L);
        folders.put(9L, deep);
        when(accessService.current()).thenReturn(manageCtx());
        when(folderRepository.findSubtree("/1/2/")).thenReturn(List.of(f2, f3));
        // f2 (depth 2, cây con sâu tới 3) dời vào thư mục độ sâu 7 -> 8, 9 > 8
        assertThatThrownBy(() -> service.update(2L, DocFolderUpsertRequest.builder().parentId(9L).name("F2").build()))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_TOO_DEEP);
    }

    @Test
    void delete_nonEmpty_rejected_andEmpty_softDeletes() {
        when(accessService.current()).thenReturn(manageCtx());
        when(folderRepository.countByParentIdAndIsDeleted(1L, 0)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(1L))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_NOT_EMPTY);

        when(folderRepository.countByParentIdAndIsDeleted(3L, 0)).thenReturn(0L);
        when(itemRepository.countByFolderIdAndIsDeleted(3L, 0)).thenReturn(0L);
        service.delete(3L);
        assertThat(f3.getIsDeleted()).isEqualTo(1);
        verify(shareRepository).deleteByFolderId(3L);
        verify(shareRepository, never()).deleteByFolderId(1L);
    }

    @Test
    void tree_marksAncestorsNavigationOnly_andCountsVisibleItems() {
        // Người dùng chỉ được chia sẻ thư mục 3 -> 1, 2 chỉ điều hướng; 5 ẩn.
        DocumentAccessService.Context ctx = DocTestData.ctx(DocTestData.staff(7L, Permissions.TEACHER_ROLE),
                false, true, false, false, false, Set.of(), folders, Set.of(3L), Set.of());
        when(accessService.current()).thenReturn(ctx);
        when(itemRepository.countByFolder()).thenReturn(List.of(new Object[]{3L, 4L}, new Object[]{5L, 2L}));
        when(itemRepository.findIdAndFolderByOwner(7L)).thenReturn(List.of());
        List<DocFolderTreeNodeDto> tree = service.getTree();
        assertThat(tree).hasSize(1);
        DocFolderTreeNodeDto root = tree.get(0);
        assertThat(root.getId()).isEqualTo(1L);
        assertThat(root.isNavigationOnly()).isTrue();
        DocFolderTreeNodeDto n3 = root.getChildren().get(0).getChildren().get(0);
        assertThat(n3.getId()).isEqualTo(3L);
        assertThat(n3.isNavigationOnly()).isFalse();
        assertThat(n3.getItemCount()).isEqualTo(4L);
    }

    @Test
    void getFolder_hidden_is404() {
        DocumentAccessService.Context ctx = DocTestData.ctx(DocTestData.staff(7L, Permissions.TEACHER_ROLE),
                false, true, false, false, false, Set.of(), folders, Set.of(3L), Set.of());
        when(accessService.current()).thenReturn(ctx);
        when(itemRepository.findIdAndFolderByOwner(anyLong())).thenReturn(List.of());
        assertThatThrownBy(() -> service.getFolder(5L))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_NOT_FOUND);
        DocFolderDto nav = service.getFolder(1L);
        assertThat(nav.isNavigationOnly()).isTrue();
        assertThat(nav.getPermissions().isCanUpload()).isFalse();
        verify(shareService, never()).describeFolderShares(any(), eq(f1), anyBoolean());
    }
}