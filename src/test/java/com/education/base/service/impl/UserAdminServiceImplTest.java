package com.education.base.service.impl;

import com.education.base.dto.request.UserCreateRequest;
import com.education.base.dto.request.UserUpdateRequest;
import com.education.base.dto.response.UserResponseDto;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.UserEntity;
import com.education.base.entity.UserRoleEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.RoleRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.UserRoleRepository;
import com.education.base.service.RefreshTokenService;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceImplTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final RoleEntity ADMIN_ROLE = RoleEntity.builder()
            .id(1L).roleCode("ROLE_ADMIN").roleName("Quản trị").status("ACTIVE").isDeleted(0).build();
    private static final RoleEntity TEACHER_ROLE = RoleEntity.builder()
            .id(2L).roleCode("ROLE_TEACHER").roleName("Giáo viên").status("ACTIVE").isDeleted(0).build();

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RefreshTokenService refreshTokenService;

    private UserAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAdminServiceImpl(userRepository, roleRepository, userRoleRepository, ENCODER,
                refreshTokenService);
        lenient().when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        TestSecurityContexts.loginAdmin(1L);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    /** Người thao tác có đủ quyền menu quản trị người dùng nhưng KHÔNG phải ROLE_ADMIN. */
    private static void loginUserManager() {
        TestSecurityContexts.login(5L, List.of("ROLE_SUPPORT"), Set.of(
                "MENU_USER_LIST:VIEW", "MENU_USER_LIST:CREATE", "MENU_USER_LIST:UPDATE", "MENU_USER_LIST:DELETE"));
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo(code);
    }

    private UserEntity givenUser(long id, String status, RoleEntity... roles) {
        UserEntity user = UserEntity.builder()
                .id(id).username("user" + id).fullName("User " + id)
                .passwordHash(ENCODER.encode("Old@1234")).status(status)
                .isDeleted(0).mustChangePassword(0).tokenVersion(0).build();
        when(userRepository.findByIdAndIsDeleted(id, 0)).thenReturn(Optional.of(user));
        List<UserRoleEntity> links = java.util.Arrays.stream(roles)
                .map(role -> UserRoleEntity.builder().userId(id).roleId(role.getId()).role(role).build())
                .toList();
        lenient().when(userRoleRepository.findWithRoleByUserIdIn(List.of(id))).thenReturn(links);
        return user;
    }

    @Test
    void delete_self_isRejected() {
        givenUser(1L, "ACTIVE", ADMIN_ROLE);

        assertThatThrownBy(() -> service.delete(1L, 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("CANNOT_DELETE_SELF");
        verify(userRepository, never()).save(any());
    }

    @Test
    void delete_lastAdmin_isRejected() {
        givenUser(2L, "ACTIVE", ADMIN_ROLE);
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L)).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(2L, 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("LAST_ADMIN");
    }

    @Test
    void delete_adminWhenAnotherAdminExists_softDeletesAndRevokesTokens() {
        UserEntity user = givenUser(2L, "ACTIVE", ADMIN_ROLE);
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L)).thenReturn(1L);

        service.delete(2L, 1L);

        assertThat(user.getIsDeleted()).isEqualTo(1);
        verifyAccessRevoked(2L);
    }

    @Test
    void lock_self_isRejected() {
        givenUser(1L, "ACTIVE", ADMIN_ROLE);

        assertThatThrownBy(() -> service.changeStatus(1L, false, 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("CANNOT_LOCK_SELF");
    }

    @Test
    void lock_lastAdmin_isRejected() {
        givenUser(2L, "ACTIVE", ADMIN_ROLE);
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L)).thenReturn(0L);

        assertThatThrownBy(() -> service.changeStatus(2L, false, 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("LAST_ADMIN");
    }

    @Test
    void lock_regularUser_setsLockedAndRevokesTokens() {
        UserEntity user = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        UserResponseDto dto = service.changeStatus(3L, false, 1L);

        assertThat(user.getStatus()).isEqualTo("LOCKED");
        verifyAccessRevoked(3L);
        verify(userRepository, never()).clearLoginFailures(any());
        assertThat(dto.isActive()).isFalse();
        verify(userRepository, never()).countActiveUsersWithRoleExcluding(any(), any());
    }

    @Test
    void unlock_setsActive() {
        UserEntity user = givenUser(3L, "LOCKED", TEACHER_ROLE);

        service.changeStatus(3L, true, 1L);

        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).clearLoginFailures(3L);
        verifyNoAccessChange();
    }

    @Test
    void resetPassword_forcesChangeAndRevokesTokens() {
        UserEntity user = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.resetPassword(3L, "Reset@5678", 1L);

        assertThat(ENCODER.matches("Reset@5678", user.getPasswordHash())).isTrue();
        assertThat(user.getMustChangePassword()).isEqualTo(1);
        verifyAccessRevoked(3L);
        verify(userRepository).clearLoginFailures(3L);
    }

    @Test
    void resetPassword_self_isRejected() {
        givenUser(1L, "ACTIVE", ADMIN_ROLE);

        assertThatThrownBy(() -> service.resetPassword(1L, "Reset@5678", 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("CANNOT_RESET_SELF");
        verifyNoAccessChange();
        verify(userRepository, never()).clearLoginFailures(any());
    }

    @Test
    void assignRoles_removingAdminFromLastAdmin_isRejected() {
        givenUser(2L, "ACTIVE", ADMIN_ROLE);
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(TEACHER_ROLE));
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L)).thenReturn(0L);

        assertThatThrownBy(() -> service.assignRoles(2L, List.of(2L)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("LAST_ADMIN");
        verify(userRoleRepository, never()).deleteAll(anyList());
    }

    @Test
    void assignRoles_replacesLinks() {
        givenUser(3L, "ACTIVE", TEACHER_ROLE);
        RoleEntity accountant = RoleEntity.builder()
                .id(3L).roleCode("ROLE_ACCOUNTANT").roleName("Kế toán").status("ACTIVE").isDeleted(0).build();
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(accountant));
        UserRoleEntity oldLink = UserRoleEntity.builder().userId(3L).roleId(2L).build();
        when(userRoleRepository.findByUserId(3L)).thenReturn(List.of(oldLink));

        service.assignRoles(3L, List.of(3L));

        verify(userRoleRepository).deleteAll(List.of(oldLink));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserRoleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(UserRoleEntity::getRoleId).containsExactly(3L);
    }

    @Test
    void assignRoles_unknownRole_isRejected() {
        givenUser(3L, "ACTIVE", TEACHER_ROLE);
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.assignRoles(3L, List.of(99L)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("ROLE_NOT_FOUND");
    }

    @Test
    void create_duplicateUsername_isRejected() {
        when(userRepository.existsByUsername("teacher9")).thenReturn(true);
        UserCreateRequest request = UserCreateRequest.builder()
                .username("Teacher9").password("Pass@1234").fullName("GV 9").build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("USERNAME_DUPLICATED");
    }

    @Test
    void create_hashesPasswordAndForcesChangeByDefault() {
        when(userRepository.existsByUsername("teacher9")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity entity = inv.getArgument(0);
            entity.setId(50L);
            return entity;
        });
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(TEACHER_ROLE));
        UserCreateRequest request = UserCreateRequest.builder()
                .username("Teacher9").password("Pass@1234").fullName("GV 9").roleIds(List.of(2L)).build();

        UserResponseDto dto = service.create(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("teacher9");
        assertThat(ENCODER.matches("Pass@1234", saved.getPasswordHash())).isTrue();
        assertThat(saved.getMustChangePassword()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getId()).isEqualTo(50L);
        verify(userRoleRepository).saveAll(anyList());
    }

    // ---- C1: chỉ ROLE_ADMIN được thao tác trên tài khoản / vai trò quản trị ----------------------------------

    @Test
    void create_withAdminRole_byNonAdmin_isForbidden() {
        loginUserManager();
        when(userRepository.existsByUsername("boss")).thenReturn(false);
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(ADMIN_ROLE));
        UserCreateRequest request = UserCreateRequest.builder()
                .username("boss").password("Pass@1234").fullName("Boss").roleIds(List.of(1L)).build();

        assertForbidden(() -> service.create(request), "ADMIN_ROLE_ASSIGNMENT_FORBIDDEN");
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).saveAll(anyList());
    }

    @Test
    void create_withAdminRole_byAdmin_isAllowed() {
        when(userRepository.existsByUsername("boss")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity entity = inv.getArgument(0);
            entity.setId(51L);
            return entity;
        });
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(ADMIN_ROLE));
        UserCreateRequest request = UserCreateRequest.builder()
                .username("boss").password("Pass@1234").fullName("Boss").roleIds(List.of(1L)).build();

        assertThat(service.create(request).getId()).isEqualTo(51L);
        verify(userRoleRepository).saveAll(anyList());
    }

    @Test
    void create_withRegularRole_byNonAdmin_isAllowed() {
        loginUserManager();
        when(userRepository.existsByUsername("teacher9")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity entity = inv.getArgument(0);
            entity.setId(52L);
            return entity;
        });
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(TEACHER_ROLE));
        UserCreateRequest request = UserCreateRequest.builder()
                .username("teacher9").password("Pass@1234").fullName("GV 9").roleIds(List.of(2L)).build();

        service.create(request);

        verify(userRoleRepository).saveAll(anyList());
    }

    @Test
    void update_adminAccount_byNonAdmin_isForbidden() {
        loginUserManager();
        UserEntity admin = givenUser(2L, "ACTIVE", ADMIN_ROLE);

        assertForbidden(() -> service.update(2L, UserUpdateRequest.builder().fullName("Hacked").build()),
                "ADMIN_ACCOUNT_PROTECTED");
        assertThat(admin.getFullName()).isEqualTo("User 2");
        verify(userRepository, never()).save(any());
    }

    @Test
    void update_adminAccount_byAdmin_isAllowed() {
        UserEntity admin = givenUser(2L, "ACTIVE", ADMIN_ROLE);

        service.update(2L, UserUpdateRequest.builder().fullName("Quản trị 2").build());

        assertThat(admin.getFullName()).isEqualTo("Quản trị 2");
    }

    @Test
    void update_regularAccount_byNonAdmin_isAllowed() {
        loginUserManager();
        UserEntity teacher = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.update(3L, UserUpdateRequest.builder().fullName("GV Mới").build());

        assertThat(teacher.getFullName()).isEqualTo("GV Mới");
    }

    @Test
    void update_adminAccount_withoutSecurityContext_isForbidden() {
        TestSecurityContexts.clear();
        givenUser(2L, "ACTIVE", ADMIN_ROLE);

        assertForbidden(() -> service.update(2L, UserUpdateRequest.builder().fullName("X").build()),
                "ADMIN_ACCOUNT_PROTECTED");
    }

    @Test
    void delete_adminAccount_byNonAdmin_isForbidden() {
        loginUserManager();
        UserEntity admin = givenUser(2L, "ACTIVE", ADMIN_ROLE);

        assertForbidden(() -> service.delete(2L, 5L), "ADMIN_ACCOUNT_PROTECTED");
        assertThat(admin.getIsDeleted()).isZero();
        verify(userRepository, never()).countActiveUsersWithRoleExcluding(any(), any());
    }

    @Test
    void delete_regularAccount_byNonAdmin_isAllowed() {
        loginUserManager();
        UserEntity teacher = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.delete(3L, 5L);

        assertThat(teacher.getIsDeleted()).isEqualTo(1);
    }

    @Test
    void lock_adminAccount_byNonAdmin_isForbidden() {
        loginUserManager();
        UserEntity admin = givenUser(2L, "ACTIVE", ADMIN_ROLE);

        assertForbidden(() -> service.changeStatus(2L, false, 5L), "ADMIN_ACCOUNT_PROTECTED");
        assertThat(admin.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void unlock_adminAccount_byNonAdmin_isForbidden() {
        loginUserManager();
        UserEntity admin = givenUser(2L, "LOCKED", ADMIN_ROLE);

        assertForbidden(() -> service.changeStatus(2L, true, 5L), "ADMIN_ACCOUNT_PROTECTED");
        assertThat(admin.getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void unlock_adminAccount_byAdmin_isAllowed() {
        UserEntity admin = givenUser(2L, "LOCKED", ADMIN_ROLE);

        service.changeStatus(2L, true, 1L);

        assertThat(admin.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void lock_regularAccount_byNonAdmin_isAllowed() {
        loginUserManager();
        UserEntity teacher = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.changeStatus(3L, false, 5L);

        assertThat(teacher.getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void resetPassword_adminAccount_byNonAdmin_isForbidden() {
        loginUserManager();
        UserEntity admin = givenUser(2L, "ACTIVE", ADMIN_ROLE);
        String before = admin.getPasswordHash();

        assertForbidden(() -> service.resetPassword(2L, "Reset@5678", 5L), "ADMIN_ACCOUNT_PROTECTED");
        assertThat(admin.getPasswordHash()).isEqualTo(before);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_adminAccount_byAdmin_isAllowed() {
        UserEntity admin = givenUser(2L, "ACTIVE", ADMIN_ROLE);

        service.resetPassword(2L, "Reset@5678", 1L);

        assertThat(ENCODER.matches("Reset@5678", admin.getPasswordHash())).isTrue();
    }

    @Test
    void resetPassword_regularAccount_byNonAdmin_isAllowed() {
        loginUserManager();
        UserEntity teacher = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.resetPassword(3L, "Reset@5678", 5L);

        assertThat(ENCODER.matches("Reset@5678", teacher.getPasswordHash())).isTrue();
    }

    @Test
    void assignRoles_self_isForbiddenEvenForAdmin() {
        givenUser(1L, "ACTIVE", ADMIN_ROLE);

        assertForbidden(() -> service.assignRoles(1L, List.of(1L, 2L)), "CANNOT_CHANGE_OWN_ROLES");
        verify(userRoleRepository, never()).saveAll(anyList());
        verify(userRoleRepository, never()).deleteAll(anyList());
    }

    @Test
    void assignRoles_self_isForbiddenForNonAdmin() {
        loginUserManager();
        givenUser(5L, "ACTIVE", TEACHER_ROLE);

        assertForbidden(() -> service.assignRoles(5L, List.of(2L)), "CANNOT_CHANGE_OWN_ROLES");
        verify(roleRepository, never()).findByIdInAndIsDeleted(any(), any());
    }

    @Test
    void assignRoles_grantAdmin_byNonAdmin_isForbidden() {
        loginUserManager();
        givenUser(3L, "ACTIVE", TEACHER_ROLE);
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(ADMIN_ROLE, TEACHER_ROLE));

        assertForbidden(() -> service.assignRoles(3L, List.of(1L, 2L)), "ADMIN_ROLE_ASSIGNMENT_FORBIDDEN");
        verify(userRoleRepository, never()).saveAll(anyList());
    }

    @Test
    void assignRoles_ofAdminAccount_byNonAdmin_isForbidden() {
        loginUserManager();
        givenUser(2L, "ACTIVE", ADMIN_ROLE);

        assertForbidden(() -> service.assignRoles(2L, List.of(2L)), "ADMIN_ACCOUNT_PROTECTED");
        verify(userRoleRepository, never()).deleteAll(anyList());
    }

    @Test
    void assignRoles_regularRoles_byNonAdmin_isAllowed() {
        loginUserManager();
        givenUser(3L, "ACTIVE", TEACHER_ROLE);
        RoleEntity accountant = RoleEntity.builder()
                .id(3L).roleCode("ROLE_ACCOUNTANT").roleName("Kế toán").status("ACTIVE").isDeleted(0).build();
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(accountant));

        service.assignRoles(3L, List.of(3L));

        verify(userRoleRepository).saveAll(anyList());
    }

    @Test
    void assignRoles_grantAdmin_byAdmin_isAllowed() {
        givenUser(3L, "ACTIVE", TEACHER_ROLE);
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(ADMIN_ROLE, TEACHER_ROLE));

        service.assignRoles(3L, List.of(1L, 2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserRoleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(UserRoleEntity::getRoleId).containsExactlyInAnyOrder(1L, 2L);
    }

    // ---- L2: kiểm tra "quản trị viên cuối cùng" an toàn khi chạy song song ---------------------------------

    @Test
    void delete_admin_locksAdminRoleRowsBeforeCounting() {
        givenUser(2L, "ACTIVE", ADMIN_ROLE);
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L)).thenReturn(1L);

        service.delete(2L, 1L);

        InOrder order = inOrder(userRoleRepository, userRepository);
        order.verify(userRoleRepository).lockByRoleCode("ROLE_ADMIN");
        order.verify(userRoleRepository).findWithRoleByUserIdIn(List.of(2L));
        order.verify(userRepository).countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L);
    }

    @Test
    void lock_admin_locksAdminRoleRowsBeforeCounting() {
        givenUser(2L, "ACTIVE", ADMIN_ROLE);
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L)).thenReturn(0L);

        assertThatThrownBy(() -> service.changeStatus(2L, false, 1L))
                .extracting("errorCode").isEqualTo("LAST_ADMIN");

        InOrder order = inOrder(userRoleRepository, userRepository);
        order.verify(userRoleRepository).lockByRoleCode("ROLE_ADMIN");
        order.verify(userRepository).countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L);
    }

    @Test
    void removeAdminRole_locksAdminRoleRowsBeforeCounting() {
        givenUser(2L, "ACTIVE", ADMIN_ROLE);
        when(userRoleRepository.findByUserId(2L))
                .thenReturn(List.of(UserRoleEntity.builder().userId(2L).roleId(1L).build()));
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(TEACHER_ROLE));
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L)).thenReturn(1L);

        service.assignRoles(2L, List.of(2L));

        InOrder order = inOrder(userRoleRepository, userRepository);
        order.verify(userRoleRepository).lockByRoleCode("ROLE_ADMIN");
        order.verify(userRepository).countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L);
        order.verify(userRoleRepository).deleteAll(anyList());
    }

    @Test
    void delete_inactiveUser_skipsAdminLock() {
        givenUser(3L, "LOCKED", TEACHER_ROLE);

        service.delete(3L, 1L);

        verify(userRoleRepository, never()).lockByRoleCode(any());
    }

    @Test
    void resetPassword_bumpsTokenVersionAtomicallyAfterSavingUser() {
        UserEntity user = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.resetPassword(3L, "Reset@5678", 1L);

        InOrder order = inOrder(userRepository, refreshTokenService);
        order.verify(userRepository).save(user);
        order.verify(userRepository).incrementTokenVersion(3L);
        order.verify(refreshTokenService).revokeAllSessions(3L);
        // Không tự cộng trên entity (có thể đã cũ): TOKEN_VERSION chỉ tăng bằng UPDATE nguyên tử ở DB.
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void lock_bumpsTokenVersionAtomically_notOnEntity() {
        UserEntity user = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.changeStatus(3L, false, 1L);

        InOrder order = inOrder(userRepository, refreshTokenService);
        order.verify(userRepository).save(user);
        order.verify(userRepository).incrementTokenVersion(3L);
        order.verify(refreshTokenService).revokeAllSessions(3L);
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void delete_bumpsTokenVersionAtomically_notOnEntity() {
        UserEntity user = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.delete(3L, 1L);

        verifyAccessRevoked(3L);
        verify(userRepository, never()).clearLoginFailures(any());
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void unlock_activeUser_clearsTemporaryLockout() {
        givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.changeStatus(3L, true, 1L);

        verify(userRepository).clearLoginFailures(3L);
        verifyNoAccessChange();
    }

    @Test
    void delete_self_doesNotRevokeAccess() {
        givenUser(1L, "ACTIVE", ADMIN_ROLE);

        assertThatThrownBy(() -> service.delete(1L, 1L)).isInstanceOf(OracleBusinessException.class);

        verifyNoAccessChange();
    }

    @Test
    void lock_lastAdmin_doesNotRevokeAccess() {
        givenUser(2L, "ACTIVE", ADMIN_ROLE);
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 2L)).thenReturn(0L);

        assertThatThrownBy(() -> service.changeStatus(2L, false, 1L)).isInstanceOf(OracleBusinessException.class);

        verifyNoAccessChange();
    }

    @Test
    void resetPassword_adminAccount_byNonAdmin_doesNotTouchTokensOrLockout() {
        givenUser(2L, "ACTIVE", ADMIN_ROLE);
        loginUserManager();

        assertForbidden(() -> service.resetPassword(2L, "Reset@5678", 5L), "ADMIN_ACCOUNT_PROTECTED");

        verifyNoAccessChange();
        verify(userRepository, never()).clearLoginFailures(any());
    }

    @Test
    void unlock_adminAccount_byNonAdmin_doesNotClearLockout() {
        givenUser(2L, "LOCKED", ADMIN_ROLE);
        loginUserManager();

        assertForbidden(() -> service.changeStatus(2L, true, 5L), "ADMIN_ACCOUNT_PROTECTED");

        verify(userRepository, never()).clearLoginFailures(any());
    }

    /** TOKEN_VERSION tăng nguyên tử ở DB và mọi phiên refresh token của người dùng bị thu hồi. */
    private void verifyAccessRevoked(Long userId) {
        verify(userRepository).incrementTokenVersion(userId);
        verify(refreshTokenService).revokeAllSessions(userId);
    }

    private void verifyNoAccessChange() {
        verify(userRepository, never()).incrementTokenVersion(any());
        verify(refreshTokenService, never()).revokeAllSessions(any());
    }
}
