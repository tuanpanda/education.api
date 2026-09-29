package com.education.base.service.impl;

import com.education.base.dto.request.MenuReorderRequest;
import com.education.base.dto.request.MenuUpsertRequest;
import com.education.base.dto.response.AdminMenuResponseDto;
import com.education.base.entity.FunctionEntity;
import com.education.base.entity.MenuEntity;
import com.education.base.entity.RoleMenuPermissionEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FunctionRepository;
import com.education.base.repository.MenuRepository;
import com.education.base.repository.RoleMenuPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuAdminServiceImplTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private FunctionRepository functionRepository;
    @Mock
    private RoleMenuPermissionRepository roleMenuPermissionRepository;

    private MenuAdminServiceImpl service;

    private final List<MenuEntity> menus = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MenuAdminServiceImpl(menuRepository, functionRepository, roleMenuPermissionRepository);
        menus.add(menu(300L, null, "DIR_SYSTEM", "DIR", 9));
        menus.add(menu(303L, 300L, "MENU_USER_LIST", "MENU", 1));
        menus.add(menu(100L, null, "DIR_ACADEMIC", "DIR", 1));
        menus.add(menu(101L, 100L, "MENU_STUDENT_LIST", "MENU", 1));
        menus.add(menu(110L, 100L, "MENU_EMPTY", "MENU", 2));
        lenient().when(menuRepository.findByIsDeletedOrderBySortOrderAscIdAsc(0)).thenAnswer(inv -> menus);
        lenient().when(menuRepository.findByIdAndIsDeleted(any(), any())).thenAnswer(inv -> menus.stream()
                .filter(m -> m.getId().equals(inv.getArgument(0))).findFirst());
        lenient().when(menuRepository.save(any(MenuEntity.class))).thenAnswer(inv -> {
            MenuEntity entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(500L);
                menus.add(entity);
            }
            return entity;
        });
    }

    private static MenuEntity menu(Long id, Long parentId, String code, String type, int sort) {
        return MenuEntity.builder().id(id).parentId(parentId).menuCode(code).menuName(code).menuType(type)
                .sortOrder(sort).status("ACTIVE").isHidden(0).isDeleted(0).build();
    }

    @Test
    void getTree_nestsChildrenOrderedBySort() {
        List<AdminMenuResponseDto> tree = service.getTree();

        assertThat(tree).extracting(AdminMenuResponseDto::getCode).containsExactly("DIR_ACADEMIC", "DIR_SYSTEM");
        assertThat(tree.get(0).getChildren()).extracting(AdminMenuResponseDto::getCode)
                .containsExactly("MENU_STUDENT_LIST", "MENU_EMPTY");
    }

    @Test
    void create_duplicateCode_isRejected() {
        when(menuRepository.existsByMenuCode("MENU_NEW")).thenReturn(true);

        assertThatThrownBy(() -> service.create(MenuUpsertRequest.builder().code("MENU_NEW").name("Mới").build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("MENU_CODE_DUPLICATED");
    }

    @Test
    void create_menuWithPath_getsDefaultCrudFunctions() {
        when(menuRepository.existsByMenuCode("MENU_NEW")).thenReturn(false);

        AdminMenuResponseDto created = service.create(MenuUpsertRequest.builder()
                .parentId(100L).code("MENU_NEW").name("Mới").path("/new").build());

        assertThat(created.getId()).isEqualTo(500L);
        MenuEntity saved = menus.get(menus.size() - 1);
        assertThat(saved.getMenuType()).isEqualTo("MENU");
        assertThat(saved.getSortOrder()).isEqualTo(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FunctionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(functionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(FunctionEntity::getFunctionCode)
                .containsExactly("VIEW", "CREATE", "UPDATE", "DELETE");
    }

    @Test
    void update_changingCode_isRejected() {
        assertThatThrownBy(() -> service.update(101L, MenuUpsertRequest.builder()
                .code("MENU_RENAMED").name("x").build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("MENU_CODE_IMMUTABLE");
    }

    @Test
    void update_parentIsOwnDescendant_isRejected() {
        assertThatThrownBy(() -> service.update(100L, MenuUpsertRequest.builder()
                .code("DIR_ACADEMIC").name("x").parentId(101L).build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("MENU_PARENT_INVALID");
    }

    @Test
    void update_removedFunction_isSoftDeletedAndStrippedFromRoles() {
        FunctionEntity view = FunctionEntity.builder().id(1L).menuId(101L).functionCode("VIEW").isDeleted(0).build();
        FunctionEntity export = FunctionEntity.builder().id(2L).menuId(101L).functionCode("EXPORT").isDeleted(0).build();
        when(functionRepository.findByMenuId(101L)).thenReturn(List.of(view, export));
        RoleMenuPermissionEntity row = RoleMenuPermissionEntity.builder()
                .roleId(2L).menuId(101L).allowedFunctions("VIEW,EXPORT").build();
        when(roleMenuPermissionRepository.findByMenuId(101L)).thenReturn(List.of(row));

        service.update(101L, MenuUpsertRequest.builder()
                .code("MENU_STUDENT_LIST").name("Học sinh").path("/students/list").functionCodes(List.of("VIEW")).build());

        assertThat(export.getIsDeleted()).isEqualTo(1);
        assertThat(view.getIsDeleted()).isEqualTo(0);
        assertThat(row.getAllowedFunctions()).isEqualTo("VIEW");
    }

    @Test
    void delete_protectedMenu_isRejected() {
        assertThatThrownBy(() -> service.delete(303L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("MENU_PROTECTED");
    }

    @Test
    void delete_menuWithChildren_isRejected() {
        when(menuRepository.existsByParentIdAndIsDeleted(100L, 0)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(100L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("MENU_HAS_CHILDREN");
        verify(menuRepository, never()).save(any());
    }

    @Test
    void delete_leafMenu_softDeletesMenuFunctionsAndPermissions() {
        when(menuRepository.existsByParentIdAndIsDeleted(110L, 0)).thenReturn(false);
        FunctionEntity view = FunctionEntity.builder().id(9L).menuId(110L).functionCode("VIEW").isDeleted(0).build();
        when(functionRepository.findByMenuIdAndIsDeleted(110L, 0)).thenReturn(List.of(view));
        when(roleMenuPermissionRepository.findByMenuId(110L)).thenReturn(List.of(
                RoleMenuPermissionEntity.builder().roleId(2L).menuId(110L).allowedFunctions("VIEW").build()));

        service.delete(110L);

        assertThat(menus.stream().filter(m -> m.getId() == 110L).findFirst().orElseThrow().getIsDeleted()).isEqualTo(1);
        assertThat(view.getIsDeleted()).isEqualTo(1);
        verify(roleMenuPermissionRepository).deleteAll(anyList());
    }

    @Test
    void reorder_cycle_isRejected() {
        MenuReorderRequest request = MenuReorderRequest.builder().items(List.of(
                MenuReorderRequest.Item.builder().id(100L).parentId(101L).sortOrder(1).build())).build();

        assertThatThrownBy(() -> service.reorder(request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("MENU_PARENT_INVALID");
        verify(menuRepository, never()).saveAll(anyList());
    }

    @Test
    void reorder_updatesOnlyChangedMenus() {
        MenuReorderRequest request = MenuReorderRequest.builder().items(List.of(
                MenuReorderRequest.Item.builder().id(101L).parentId(100L).sortOrder(2).build(),
                MenuReorderRequest.Item.builder().id(110L).parentId(100L).sortOrder(1).build(),
                MenuReorderRequest.Item.builder().id(100L).parentId(null).sortOrder(1).build())).build();

        List<AdminMenuResponseDto> tree = service.reorder(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(menuRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(MenuEntity::getId).containsExactly(101L, 110L);
        assertThat(tree.get(0).getChildren()).extracting(AdminMenuResponseDto::getCode)
                .containsExactly("MENU_EMPTY", "MENU_STUDENT_LIST");
    }

    @Test
    void getById_missing_isRejected() {
        when(menuRepository.findByIdAndIsDeleted(999L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("MENU_NOT_FOUND");
    }
}
