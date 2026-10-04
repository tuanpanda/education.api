package com.education.base.service.impl;

import com.education.base.dto.response.MenuItemResponseDto;
import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.entity.FunctionEntity;
import com.education.base.entity.MenuEntity;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.RoleMenuPermissionEntity;
import com.education.base.entity.UserEntity;
import com.education.base.entity.UserRoleEntity;
import com.education.base.repository.FunctionRepository;
import com.education.base.repository.MenuRepository;
import com.education.base.repository.RoleMenuPermissionRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.UserRoleRepository;
import com.education.base.repository.UserStudentLinkRepository;
import com.education.base.entity.UserStudentLinkEntity;
import com.education.base.security.UserType;
import com.education.base.security.AuthUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private FunctionRepository functionRepository;
    @Mock
    private RoleMenuPermissionRepository roleMenuPermissionRepository;
    @Mock
    private UserStudentLinkRepository userStudentLinkRepository;

    private AccessControlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccessControlServiceImpl(userRepository, userRoleRepository, menuRepository,
                functionRepository, roleMenuPermissionRepository, userStudentLinkRepository);
        MenuEntity hidden = menu(104L, 100L, "MENU_HIDDEN", 4);
        hidden.setIsHidden(1);
        lenient().when(menuRepository.findByIsDeletedOrderBySortOrderAscIdAsc(0)).thenReturn(List.of(
                menu(100L, null, "DIR_ACADEMIC", 1),
                menu(101L, 100L, "MENU_STUDENT_LIST", 1),
                menu(102L, 100L, "MENU_CLASS_LIST", 2),
                hidden,
                menu(300L, null, "DIR_SYSTEM", 9),
                menu(303L, 300L, "MENU_USER_LIST", 1)));
        lenient().when(functionRepository.findByIsDeletedOrderByIdAsc(0)).thenReturn(List.of(
                function(100L, "VIEW"),
                function(101L, "VIEW"), function(101L, "EXPORT"),
                function(102L, "VIEW"), function(102L, "UPDATE"),
                function(104L, "VIEW"),
                function(300L, "VIEW"),
                function(303L, "VIEW"), function(303L, "CREATE")));
    }

    private static MenuEntity menu(Long id, Long parentId, String code, int sort) {
        return MenuEntity.builder().id(id).parentId(parentId).menuCode(code).menuName(code)
                .menuType(parentId == null ? "DIR" : "MENU").sortOrder(sort).status("ACTIVE").isHidden(0).isDeleted(0).build();
    }

    private static FunctionEntity function(Long menuId, String code) {
        return FunctionEntity.builder().menuId(menuId).functionCode(code).functionName(code).isDeleted(0).build();
    }

    private static RoleEntity role(Long id, String code) {
        return RoleEntity.builder().id(id).roleCode(code).roleName(code).status("ACTIVE").isDeleted(0).build();
    }

    @Test
    void resolvePermissions_admin_getsEveryFunction() {
        Set<String> permissions = service.resolvePermissions(List.of(role(1L, "ROLE_ADMIN")));

        assertThat(permissions).contains("MENU_USER_LIST:CREATE", "MENU_CLASS_LIST:UPDATE", "DIR_SYSTEM:VIEW")
                .hasSize(9);
    }

    @Test
    void resolvePermissions_ignoresFunctionsNotDefinedOnMenu() {
        when(roleMenuPermissionRepository.findByRoleIdIn(List.of(2L))).thenReturn(List.of(
                RoleMenuPermissionEntity.builder().roleId(2L).menuId(101L).allowedFunctions("VIEW,DELETE").build()));

        Set<String> permissions = service.resolvePermissions(List.of(role(2L, "ROLE_TEACHER")));

        assertThat(permissions).containsExactly("MENU_STUDENT_LIST:VIEW");
    }

    @Test
    void getNavigation_returnsOnlyViewableMenusWithAncestors() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(5L).username("teacher1")
                .role("ROLE_TEACHER")
                .permission("MENU_CLASS_LIST:VIEW")
                .permission("MENU_CLASS_LIST:UPDATE")
                .permission("MENU_STUDENT_LIST:EXPORT")
                .permission("MENU_HIDDEN:VIEW")
                .build();

        UserNavigationResponseDto navigation = service.getNavigation(principal);

        assertThat(navigation.getMenus()).extracting(MenuItemResponseDto::getMenuCode).containsExactly("DIR_ACADEMIC");
        MenuItemResponseDto root = navigation.getMenus().get(0);
        assertThat(root.getChildren()).extracting(MenuItemResponseDto::getMenuCode).containsExactly("MENU_CLASS_LIST");
        assertThat(root.getChildren().get(0).getAllowedFunctions()).containsExactlyInAnyOrder("VIEW", "UPDATE");
        assertThat(navigation.getPermissions()).contains("MENU_STUDENT_LIST:EXPORT");
    }

    @Test
    void loadActivePrincipal_lockedUser_isEmpty() {
        when(userRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(UserEntity.builder()
                .id(5L).username("teacher1").status("LOCKED").isDeleted(0).build()));

        assertThat(service.loadActivePrincipal(5L)).isEmpty();
    }

    @Test
    void loadActivePrincipal_activeUser_includesRolesPermissionsAndVersion() {
        when(userRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(UserEntity.builder()
                .id(5L).username("teacher1").fullName("GV").status("ACTIVE").isDeleted(0)
                .mustChangePassword(1).tokenVersion(4).build()));
        RoleEntity teacher = role(2L, "ROLE_TEACHER");
        RoleEntity inactive = role(3L, "ROLE_OLD");
        inactive.setStatus("INACTIVE");
        when(userRoleRepository.findWithRoleByUserIdIn(List.of(5L))).thenReturn(List.of(
                UserRoleEntity.builder().userId(5L).roleId(2L).role(teacher).build(),
                UserRoleEntity.builder().userId(5L).roleId(3L).role(inactive).build()));
        when(roleMenuPermissionRepository.findByRoleIdIn(any())).thenReturn(List.of(
                RoleMenuPermissionEntity.builder().roleId(2L).menuId(102L).allowedFunctions("VIEW").build()));

        AuthUserPrincipal principal = service.loadActivePrincipal(5L).orElseThrow();

        assertThat(principal.getRoles()).containsExactly("ROLE_TEACHER");
        assertThat(principal.getPermissions()).containsExactly("MENU_CLASS_LIST:VIEW");
        assertThat(principal.isMustChangePassword()).isTrue();
        assertThat(principal.getTokenVersion()).isEqualTo(4);
        assertThat(principal.isAdmin()).isFalse();
    }

    @Test
    void loadActivePrincipal_staffUser_isStaffWithoutStudent() {
        when(userRepository.findByIdAndIsDeleted(5L, 0)).thenReturn(Optional.of(UserEntity.builder()
                .id(5L).username("teacher1").status("ACTIVE").userType("STAFF").isDeleted(0).build()));
        when(userRoleRepository.findWithRoleByUserIdIn(List.of(5L))).thenReturn(List.of());

        AuthUserPrincipal principal = service.loadActivePrincipal(5L).orElseThrow();

        assertThat(principal.getUserType()).isEqualTo(UserType.STAFF);
        assertThat(principal.getStudentId()).isNull();
        verifyNoInteractions(userStudentLinkRepository);
    }

    @Test
    void loadActivePrincipal_student_getsStudentIdAndNoPermissionsEvenWithStaffRole() {
        when(userRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(UserEntity.builder()
                .id(9L).username("hs00001").status("ACTIVE").userType("STUDENT").isDeleted(0)
                .mustChangePassword(1).tokenVersion(0).build()));
        RoleEntity admin = role(1L, "ROLE_ADMIN");
        when(userRoleRepository.findWithRoleByUserIdIn(List.of(9L))).thenReturn(List.of(
                UserRoleEntity.builder().userId(9L).roleId(1L).role(admin).build()));
        when(userStudentLinkRepository.findActiveSelfLinkByUserId(9L)).thenReturn(Optional.of(
                UserStudentLinkEntity.builder().userId(9L).studentId(42L).relation("SELF").status("ACTIVE").build()));

        AuthUserPrincipal principal = service.loadActivePrincipal(9L).orElseThrow();

        assertThat(principal.getUserType()).isEqualTo(UserType.STUDENT);
        assertThat(principal.getStudentId()).isEqualTo(42L);
        assertThat(principal.getPermissions()).isEmpty();
        assertThat(principal.isAdmin()).as("tài khoản học sinh không bao giờ là quản trị viên").isFalse();
        assertThat(principal.hasPermission("MENU_USER_LIST:VIEW")).isFalse();
    }

    @Test
    void loadActivePrincipal_unknownUserType_isEmpty() {
        when(userRepository.findByIdAndIsDeleted(9L, 0)).thenReturn(Optional.of(UserEntity.builder()
                .id(9L).username("x").status("ACTIVE").userType("ALIEN").isDeleted(0).build()));

        assertThat(service.loadActivePrincipal(9L)).isEmpty();
    }
}
