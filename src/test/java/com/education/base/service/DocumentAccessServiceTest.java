package com.education.base.service;

import com.education.base.entity.DocFolderEntity;
import com.education.base.entity.DocItemEntity;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.DocFolderRepository;
import com.education.base.repository.DocShareRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAccessServiceTest {

    @Mock
    private DocFolderRepository folderRepository;
    @Mock
    private DocShareRepository shareRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private ClassSessionRepository classSessionRepository;

    private DocumentAccessService service;

    // /1/ -> /1/2/ -> /1/2/3/ ; /4/ (của user 7)
    private final DocFolderEntity f1 = DocTestData.folder(1, null, "/1/", 1L);
    private final DocFolderEntity f2 = DocTestData.folder(2, 1L, "/1/2/", 1L);
    private final DocFolderEntity f3 = DocTestData.folder(3, 2L, "/1/2/3/", 1L);
    private final DocFolderEntity f4 = DocTestData.folder(4, null, "/4/", 7L);

    @BeforeEach
    void setUp() {
        service = new DocumentAccessService(folderRepository, shareRepository, classRepository, classSessionRepository);
        lenient().when(folderRepository.findByIsDeleted(0)).thenReturn(List.of(f1, f2, f3, f4));
        lenient().when(classRepository.findIdsByTeacherIdAndIsDeleted(anyLong(), anyInt())).thenReturn(List.of(15L));
        lenient().when(classSessionRepository.findDistinctClassIdsByTeacherIdAndIsDeletedAndStatusNot(
                anyLong(), anyInt(), anyString())).thenReturn(List.of(16L));
        lenient().when(shareRepository.findRoleIdsByCodes(anyCollection())).thenReturn(List.of(3L));
    }

    private AuthUserPrincipal teacher() {
        return DocTestData.staff(7L, Permissions.TEACHER_ROLE,
                Permissions.DOC_LIBRARY_VIEW, Permissions.DOC_LIBRARY_UPLOAD, Permissions.DOC_LIBRARY_DOWNLOAD);
    }

    @Test
    void taughtClasses_alwaysComputed_homeroomAndSessions() {
        assertThat(service.taughtClassIds(7L)).containsExactlyInAnyOrder(15L, 16L);
        assertThat(service.taughtClassIds(null)).isEmpty();
    }

    @Test
    void manage_seesEverything_withoutShareQueries() {
        DocumentAccessService.Context ctx = service.forUser(DocTestData.admin());
        assertThat(ctx.isManage()).isTrue();
        assertThat(ctx.folderVisible(3L)).isTrue();
        assertThat(ctx.itemVisible(DocTestData.item(9, 3L, 99))).isTrue();
        assertThat(ctx.canCreateIn(null)).isTrue();
        verify(shareRepository, never()).findSharedFolderIds(anyCollection(), anyCollection(), any());
    }

    @Test
    void noShares_onlyOwnFolderAndItems() {
        when(shareRepository.findSharedFolderIds(anyCollection(), anyCollection(), any())).thenReturn(List.of());
        when(shareRepository.findSharedItemIds(anyCollection(), anyCollection(), any())).thenReturn(List.of());
        DocumentAccessService.Context ctx = service.forUser(teacher());
        assertThat(ctx.folderVisible(1L)).isFalse();
        assertThat(ctx.folderVisible(4L)).isTrue();
        assertThat(ctx.itemVisible(DocTestData.item(9, 3L, 7))).isTrue();
        assertThat(ctx.itemVisible(DocTestData.item(10, 3L, 8))).isFalse();
        assertThat(ctx.canCreateIn(null)).isFalse();
        assertThat(ctx.canCreateIn(1L)).isFalse();
        assertThat(ctx.canCreateIn(4L)).isTrue();
    }

    @Test
    void folderShare_inheritedDownSubtree_notUpwards() {
        when(shareRepository.findSharedFolderIds(anyCollection(), anyCollection(), any())).thenReturn(List.of(2L));
        when(shareRepository.findSharedItemIds(anyCollection(), anyCollection(), any())).thenReturn(List.of());
        DocumentAccessService.Context ctx = service.forUser(teacher());
        assertThat(ctx.folderVisible(2L)).isTrue();
        assertThat(ctx.folderVisible(3L)).isTrue();
        assertThat(ctx.folderVisible(1L)).isFalse();
        assertThat(ctx.itemVisible(DocTestData.item(10, 3L, 8))).isTrue();
        assertThat(ctx.itemVisible(DocTestData.item(11, 1L, 8))).isFalse();
        assertThat(ctx.visibleFolderIds()).containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void directItemShare_visible_butNotEditableWithoutOwnership() {
        when(shareRepository.findSharedFolderIds(anyCollection(), anyCollection(), any())).thenReturn(List.of());
        when(shareRepository.findSharedItemIds(anyCollection(), anyCollection(), any())).thenReturn(List.of(10L));
        DocumentAccessService.Context ctx = service.forUser(teacher());
        DocItemEntity shared = DocTestData.item(10, 1L, 8);
        assertThat(ctx.itemVisible(shared)).isTrue();
        assertThat(ctx.canEdit(shared)).isFalse();
        assertThat(ctx.canDelete(shared)).isFalse();
        assertThat(ctx.canShare(shared)).isFalse();
        DocItemEntity own = DocTestData.item(11, 1L, 7);
        assertThat(ctx.canEdit(own)).isTrue();
        assertThat(ctx.canDelete(own)).isTrue();
        assertThat(ctx.canShare(own)).isTrue();
        assertThat(ctx.canShareAnyTarget()).isFalse();
    }

    @Test
    void shareQueries_receiveRolesAndTaughtClasses() {
        when(shareRepository.findSharedFolderIds(anyCollection(), anyCollection(), any())).thenReturn(List.of());
        when(shareRepository.findSharedItemIds(anyCollection(), anyCollection(), any())).thenReturn(List.of());
        DocumentAccessService.Context ctx = service.forUser(teacher());
        assertThat(ctx.getRoleIds()).containsExactly(3L);
        assertThat(ctx.getTaughtClassIds()).containsExactlyInAnyOrder(15L, 16L);
        verify(shareRepository).findSharedFolderIds(eq(Set.of(3L)), eq(Set.of(15L, 16L)), any());
    }

    @Test
    void updateDeleteSharePermissions_applyToVisibleItemsOnly() {
        when(shareRepository.findSharedFolderIds(anyCollection(), anyCollection(), any())).thenReturn(List.of(1L));
        when(shareRepository.findSharedItemIds(anyCollection(), anyCollection(), any())).thenReturn(List.of());
        AuthUserPrincipal lead = DocTestData.staff(8L, "ROLE_LEAD", Permissions.DOC_LIBRARY_VIEW,
                Permissions.DOC_LIBRARY_UPDATE, Permissions.DOC_LIBRARY_DELETE, Permissions.DOC_LIBRARY_SHARE);
        DocumentAccessService.Context ctx = service.forUser(lead);
        DocItemEntity visible = DocTestData.item(10, 3L, 99);
        assertThat(ctx.canEdit(visible)).isTrue();
        assertThat(ctx.canDelete(visible)).isTrue();
        assertThat(ctx.canShare(visible)).isTrue();
        assertThat(ctx.canShareFolder(2L)).isTrue();
        assertThat(ctx.canShareFolder(4L)).isFalse();
        DocItemEntity hidden = DocTestData.item(11, 4L, 99);
        assertThat(ctx.canEdit(hidden)).isFalse();
        // không có UPLOAD -> không tạo được
        assertThat(ctx.canCreateIn(2L)).isFalse();
    }

    @Test
    void student_seesNothing() {
        AuthUserPrincipal student = AuthUserPrincipal.builder().id(50L).username("hs")
                .userType(com.education.base.security.UserType.STUDENT).studentId(5L)
                .permission(Permissions.DOC_LIBRARY_VIEW).build();
        DocumentAccessService.Context ctx = service.forUser(student);
        assertThat(ctx.itemVisible(DocTestData.item(10, 1L, 50))).isFalse();
        assertThat(ctx.folderVisible(1L)).isFalse();
    }

    @Test
    void deletedItem_neverVisible() {
        DocumentAccessService.Context ctx = service.forUser(DocTestData.admin());
        DocItemEntity deleted = DocTestData.item(10, 1L, 1);
        deleted.setIsDeleted(1);
        assertThat(ctx.itemVisible(deleted)).isFalse();
    }

    @Test
    void pathIdsAndSubtree() {
        assertThat(DocumentAccessService.pathIds("/1/2/3/")).containsExactly(1L, 2L, 3L);
        DocumentAccessService.Context ctx = service.forUser(DocTestData.admin());
        assertThat(ctx.subtreeIds(2L)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(ctx.chain(f3)).extracting(DocFolderEntity::getId).containsExactly(1L, 2L, 3L);
    }
}