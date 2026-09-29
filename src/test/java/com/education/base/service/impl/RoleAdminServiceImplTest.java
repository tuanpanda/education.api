package com.education.base.service.impl;

import com.education.base.dto.request.RoleCreateRequest;
import com.education.base.dto.request.RolePermissionUpdateRequest;
import com.education.base.dto.request.RoleUpdateRequest;
import com.education.base.dto.response.PermissionMenuNodeDto;
import com.education.base.dto.response.RolePermissionMatrixResponse;
import com.education.base.entity.FunctionEntity;
import com.education.base.entity.MenuEntity;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.RoleMenuPermissionEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FunctionRepository;
import com.education.base.repository.MenuRepository;
import com.education.base.repository.RoleMenuPermissionRepository;
import com.education.base.repository.RoleRepository;
import com.education.base.repository.UserRoleRepository;
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
class RoleAdminServiceImplTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private FunctionRepository functionRepository;
    @Mock
    private RoleMenuPermissionRepository roleMenuPermissionRepository;

    private RoleAdminServiceImpl service;

    private final List<RoleMenuPermissionEntity> storedPermissions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new RoleAdminServiceImpl(roleRepository, userRoleRepository, menuRepository,
                functionRepository, roleMenuPermissionRepository);
        lenient().when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(menuRepository.findByIsDeletedOrderBySortOrderAscIdAsc(0)).thenReturn(List.of(
                menu(100L, null, "DIR_ACADEMIC", "DIR", 1),
                menu(101L, 100L, "MENU_STUDENT_LIST", "MENU", 1),
                menu(102L, 100L, "MENU_CLASS_LIST", "MENU", 2)));
        lenient().when(functionRepository.findByIsDeletedOrderByIdAsc(0)).thenReturn(List.of(
                function(1L, 100L, "VIEW"),
                function(2L, 101L, "VIEW"), function(3L, 101L, "CREATE"), function(4L, 101L, "EXPORT"),
                function(5L, 102L, "VIEW"), function(6L, 102L, "UPDATE")));
        lenient().when(roleMenuPermissionRepository.findByRoleId(any())).thenAnswer(inv -> storedPermissions);
    }

    private static MenuEntity menu(Long id, Long parentId, String code, String type, int sort) {
        return MenuEntity.builder().id(id).parentId(parentId).menuCode(code).menuName(code).menuType(type)
                .sortOrder(sort).status("ACTIVE").isHidden(0).isDeleted(0).build();
    }

    private static FunctionEntity function(Long id, Long menuId, String code) {
        return FunctionEntity.builder().id(id).menuId(menuId).functionCode(code).functionName(code).isDeleted(0).build();
    }

    private static RoleEntity role(Long id, String code) {
        return RoleEntity.builder().id(id).roleCode(code).roleName(code).status("ACTIVE").isDeleted(0).build();
    }

    @Test
    void create_duplicateCode_isRejected() {
        when(roleRepository.existsByRoleCode("ROLE_TEACHER")).thenReturn(true);

        assertThatThrownBy(() -> service.create(RoleCreateRequest.builder()
                .roleCode("ROLE_TEACHER").roleName("GV").build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ROLE_CODE_DUPLICATED");
    }

    @Test
    void delete_adminRole_isRejected() {
        when(roleRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(role(1L, "ROLE_ADMIN")));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ADMIN_ROLE_PROTECTED");
    }

    @Test
    void delete_roleInUse_isRejected() {
        when(roleRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(role(2L, "ROLE_TEACHER")));
        when(userRoleRepository.countActiveUsersByRoleIds(List.of(2L)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 3L}));

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ROLE_IN_USE");
        verify(roleRepository, never()).save(any());
    }

    @Test
    void delete_unusedRole_softDeletesAndRemovesPermissions() {
        RoleEntity role = role(5L, "ROLE_GUEST");
        when(roleRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(role));
        when(userRoleRepository.countActiveUsersByRoleIds(List.of(5L))).thenReturn(List.of());
        storedPermissions.add(RoleMenuPermissionEntity.builder().id(9L).roleId(5L).menuId(101L).allowedFunctions("VIEW").build());

        service.delete(5L);

        assertThat(role.getIsDeleted()).isEqualTo(1);
        verify(roleMenuPermissionRepository).deleteAll(anyList());
    }

    @Test
    void update_deactivateAdminRole_isRejected() {
        when(roleRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(role(1L, "ROLE_ADMIN")));

        assertThatThrownBy(() -> service.update(1L, RoleUpdateRequest.builder()
                .roleName("Admin").active(false).build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ADMIN_ROLE_PROTECTED");
    }

    @Test
    void getPermissions_buildsTreeWithGrantedFunctions() {
        when(roleRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(role(2L, "ROLE_TEACHER")));
        storedPermissions.add(RoleMenuPermissionEntity.builder().roleId(2L).menuId(101L).allowedFunctions("VIEW,EXPORT").build());

        RolePermissionMatrixResponse matrix = service.getPermissions(2L);

        assertThat(matrix.isEditable()).isTrue();
        assertThat(matrix.getFunctions()).extracting("code").containsExactly("VIEW", "CREATE", "UPDATE", "EXPORT");
        assertThat(matrix.getMenus()).hasSize(1);
        PermissionMenuNodeDto root = matrix.getMenus().get(0);
        assertThat(root.getMenuCode()).isEqualTo("DIR_ACADEMIC");
        assertThat(root.getChildren()).extracting(PermissionMenuNodeDto::getMenuCode)
                .containsExactly("MENU_STUDENT_LIST", "MENU_CLASS_LIST");
        PermissionMenuNodeDto students = root.getChildren().get(0);
        assertThat(students.getAvailableFunctions()).containsExactly("VIEW", "CREATE", "EXPORT");
        assertThat(students.getGrantedFunctions()).containsExactly("VIEW", "EXPORT");
    }

    @Test
    void getPermissions_adminRole_isReadOnlyWithEverythingGranted() {
        when(roleRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(role(1L, "ROLE_ADMIN")));

        RolePermissionMatrixResponse matrix = service.getPermissions(1L);

        assertThat(matrix.isEditable()).isFalse();
        PermissionMenuNodeDto classes = matrix.getMenus().get(0).getChildren().get(1);
        assertThat(classes.getGrantedFunctions()).containsExactly("VIEW", "UPDATE");
    }

    @Test
    void updatePermissions_adminRole_isRejected() {
        when(roleRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(role(1L, "ROLE_ADMIN")));

        assertThatThrownBy(() -> service.updatePermissions(1L, new RolePermissionUpdateRequest()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ADMIN_ROLE_PROTECTED");
    }

    @Test
    void updatePermissions_functionNotOnMenu_isRejected() {
        when(roleRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(role(2L, "ROLE_TEACHER")));
        RolePermissionUpdateRequest request = RolePermissionUpdateRequest.builder()
                .permissions(List.of(RolePermissionUpdateRequest.MenuPermission.builder()
                        .menuId(102L).functions(List.of("DELETE")).build()))
                .build();

        assertThatThrownBy(() -> service.updatePermissions(2L, request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("INVALID_FUNCTION");
    }

    @Test
    void updatePermissions_grantsParentViewAndRevokesMissingMenus() {
        when(roleRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(role(2L, "ROLE_TEACHER")));
        RoleMenuPermissionEntity stale = RoleMenuPermissionEntity.builder()
                .id(7L).roleId(2L).menuId(102L).allowedFunctions("VIEW").build();
        storedPermissions.add(stale);
        RolePermissionUpdateRequest request = RolePermissionUpdateRequest.builder()
                .permissions(List.of(RolePermissionUpdateRequest.MenuPermission.builder()
                        .menuId(101L).functions(List.of("EXPORT", "VIEW")).build()))
                .build();

        service.updatePermissions(2L, request);

        verify(roleMenuPermissionRepository).deleteAll(List.of(stale));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoleMenuPermissionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(roleMenuPermissionRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(RoleMenuPermissionEntity::getMenuId, RoleMenuPermissionEntity::getAllowedFunctions)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(101L, "VIEW,EXPORT"),
                        org.assertj.core.groups.Tuple.tuple(100L, "VIEW"));
    }
}
