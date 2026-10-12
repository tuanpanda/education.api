package com.education.base.service.impl;

import com.education.base.common.document.DocErrorCodes;
import com.education.base.dto.request.DocShareRequest;
import com.education.base.dto.request.DocSharesUpdateRequest;
import com.education.base.dto.response.DocShareDto;
import com.education.base.dto.response.DocShareTargetsDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.DocFolderEntity;
import com.education.base.entity.DocItemEntity;
import com.education.base.entity.DocShareEntity;
import com.education.base.entity.RoleEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.DocItemRepository;
import com.education.base.repository.DocShareRepository;
import com.education.base.repository.RoleRepository;
import com.education.base.security.Permissions;
import com.education.base.service.AuditService;
import com.education.base.service.DocTestData;
import com.education.base.service.DocumentAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentShareServiceImplTest {

    @Mock
    private DocumentAccessService accessService;
    @Mock
    private DocShareRepository shareRepository;
    @Mock
    private DocItemRepository itemRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private DocumentShareServiceImpl service;

    private Map<Long, DocFolderEntity> folders;
    private DocItemEntity own;

    @BeforeEach
    void setUp() {
        folders = DocTestData.folders(DocTestData.folder(1, null, "/1/", 1L), DocTestData.folder(2, 1L, "/1/2/", 1L));
        own = DocTestData.item(40, 2L, 7);
        lenient().when(itemRepository.findByIdAndIsDeleted(40L, 0)).thenReturn(Optional.of(own));
        lenient().when(shareRepository.findByItemIdOrderByIdAsc(40L)).thenReturn(List.of());
        lenient().when(shareRepository.findByFolderIdIn(anyCollection())).thenReturn(List.of());
        lenient().when(classRepository.findByIdInAndIsDeletedOrderByClassCode(anyCollection(), eq(0)))
                .thenAnswer(inv -> ((java.util.Collection<Long>) inv.getArgument(0)).stream()
                        .map(id -> ClassEntity.builder().id(id).classCode("L" + id).build()).toList());
    }

    private DocumentAccessService.Context teacher() {
        return DocTestData.ctx(DocTestData.staff(7L, Permissions.TEACHER_ROLE), false, true, false, false, false,
                Set.of(15L), folders, Set.of(), Set.of());
    }

    private DocumentAccessService.Context admin() {
        return DocTestData.ctx(DocTestData.admin(), true, true, true, true, true, Set.of(), folders, Set.of(),
                Set.of());
    }

    private static DocSharesUpdateRequest req(DocShareRequest... shares) {
        return DocSharesUpdateRequest.builder().shares(List.of(shares)).build();
    }

    private static DocShareRequest cls(long id, boolean students) {
        return DocShareRequest.builder().targetType("CLASS").targetId(id).visibleToStudents(students).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void owner_canShareToTaughtClass_withStudentFlag() {
        when(accessService.current()).thenReturn(teacher());
        service.putItemShares(40L, req(cls(15, true)));
        ArgumentCaptor<List<DocShareEntity>> cap = ArgumentCaptor.forClass(List.class);
        verify(shareRepository).saveAll(cap.capture());
        DocShareEntity s = cap.getValue().get(0);
        assertThat(s.getItemId()).isEqualTo(40L);
        assertThat(s.getClassId()).isEqualTo(15L);
        assertThat(s.getVisibleToStudents()).isEqualTo(1);
        verify(auditService).success(eq("DOC_SHARES_CHANGED"), eq("DOC_ITEM"), eq(40L), any());
    }

    @Test
    void owner_cannotShareToOtherClass_orAllStaff() {
        when(accessService.current()).thenReturn(teacher());
        assertThatThrownBy(() -> service.putItemShares(40L, req(cls(99, false))))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_SHARE_CLASS_FORBIDDEN);
        assertThatThrownBy(() -> service.putItemShares(40L,
                req(DocShareRequest.builder().targetType("ALL_STAFF").build())))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_SHARE_CLASS_FORBIDDEN);
        verify(shareRepository, never()).saveAll(any());
    }

    @Test
    void owner_keepsExistingAdminShareUnchanged() {
        DocShareEntity existing = DocShareEntity.builder().id(5L).itemId(40L).targetType("ALL_STAFF")
                .visibleToStudents(0).build();
        when(shareRepository.findByItemIdOrderByIdAsc(40L)).thenReturn(List.of(existing));
        when(accessService.current()).thenReturn(teacher());
        service.putItemShares(40L, req(DocShareRequest.builder().targetType("ALL_STAFF").build(), cls(15, false)));
        verify(shareRepository).deleteByItemId(40L);
        verify(shareRepository).saveAll(any());
    }

    @Test
    void notOwner_withoutShareRight_forbidden() {
        DocItemEntity other = DocTestData.item(41, 2L, 99);
        when(itemRepository.findByIdAndIsDeleted(41L, 0)).thenReturn(Optional.of(other));
        when(accessService.current()).thenReturn(DocTestData.ctx(DocTestData.staff(7L, Permissions.TEACHER_ROLE),
                false, true, false, false, false, Set.of(15L), folders, Set.of(), Set.of(41L)));
        assertThatThrownBy(() -> service.putItemShares(41L, req(cls(15, false))))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FORBIDDEN);
    }

    @Test
    void duplicatesAndPastExpiry_invalid() {
        when(accessService.current()).thenReturn(admin());
        assertThatThrownBy(() -> service.putItemShares(40L, req(cls(15, false), cls(15, true))))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_SHARE_INVALID);
        DocShareRequest past = cls(15, false);
        past.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.putItemShares(40L, req(past)))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_SHARE_INVALID);
        DocShareRequest bad = DocShareRequest.builder().targetType("ROLE").targetId(3L).visibleToStudents(true).build();
        assertThatThrownBy(() -> service.putItemShares(40L, req(bad)))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_SHARE_INVALID);
    }

    @Test
    void unknownRole_targetNotFound() {
        when(accessService.current()).thenReturn(admin());
        when(roleRepository.findByIdInAndIsDeleted(anyCollection(), eq(0))).thenReturn(List.of());
        assertThatThrownBy(() -> service.putFolderShares(2L,
                req(DocShareRequest.builder().targetType("ROLE").targetId(3L).build())))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_SHARE_TARGET_NOT_FOUND);
    }

    @Test
    void folderShare_requiresShareOrManage() {
        when(accessService.current()).thenReturn(DocTestData.ctx(DocTestData.staff(7L, Permissions.TEACHER_ROLE),
                false, true, false, false, false, Set.of(15L), folders, Set.of(2L), Set.of()));
        assertThatThrownBy(() -> service.putFolderShares(2L, req()))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FORBIDDEN);
        assertThatThrownBy(() -> service.getFolderShares(1L))
                .hasFieldOrPropertyWithValue("errorCode", DocErrorCodes.DOC_FOLDER_NOT_FOUND);
    }

    @Test
    void itemShares_includeInheritedFromAncestors() {
        DocShareEntity direct = DocShareEntity.builder().id(1L).itemId(40L).targetType("CLASS").classId(15L)
                .visibleToStudents(1).build();
        DocShareEntity inherited = DocShareEntity.builder().id(2L).folderId(1L).targetType("ALL_STAFF")
                .visibleToStudents(0).build();
        when(shareRepository.findByItemIdOrderByIdAsc(40L)).thenReturn(List.of(direct));
        when(shareRepository.findByFolderIdIn(anyCollection())).thenReturn(List.of(inherited));
        when(classRepository.findAllById(any())).thenReturn(List.of(
                ClassEntity.builder().id(15L).classCode("10A1").className("Toán").build()));
        when(accessService.current()).thenReturn(teacher());
        List<DocShareDto> dtos = service.getItemShares(40L);
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).isInherited()).isFalse();
        assertThat(dtos.get(0).getTargetLabel()).isEqualTo("10A1 - Toán");
        assertThat(dtos.get(1).isInherited()).isTrue();
        assertThat(dtos.get(1).getInheritedFromFolderName()).isEqualTo("F1");
        assertThat(service.visibleToStudents(teacher(), own, List.of(direct), Map.of())).isTrue();
    }

    @Test
    void shareTargets_teacherOnlyTaughtClasses_adminEverything() {
        when(accessService.current()).thenReturn(teacher());
        DocShareTargetsDto t = service.getShareTargets();
        assertThat(t.isAllStaffAllowed()).isFalse();
        assertThat(t.getRoles()).isEmpty();
        assertThat(t.getClasses()).extracting(DocShareTargetsDto.Option::getId).containsExactly(15L);

        when(accessService.current()).thenReturn(admin());
        when(roleRepository.findAll()).thenReturn(List.of(
                RoleEntity.builder().id(2L).roleCode("ROLE_TEACHER").roleName("GV").isDeleted(0).status("ACTIVE").build(),
                RoleEntity.builder().id(9L).roleCode("ROLE_STUDENT").roleName("HS").isDeleted(0).status("ACTIVE").build()));
        when(classRepository.findAll()).thenReturn(List.of(
                ClassEntity.builder().id(15L).classCode("A").status("OPEN").isDeleted(0).build(),
                ClassEntity.builder().id(16L).classCode("B").status("CLOSED").isDeleted(0).build()));
        DocShareTargetsDto a = service.getShareTargets();
        assertThat(a.isAllStaffAllowed()).isTrue();
        assertThat(a.getRoles()).extracting(DocShareTargetsDto.Option::getId).containsExactly(2L);
        assertThat(a.getClasses()).extracting(DocShareTargetsDto.Option::getId).containsExactly(15L);
    }
}