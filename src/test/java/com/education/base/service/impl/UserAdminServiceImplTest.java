package com.education.base.service.impl;

import com.education.base.dto.request.UserCreateRequest;
import com.education.base.dto.response.UserResponseDto;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.UserEntity;
import com.education.base.entity.UserRoleEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.RoleRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    private UserAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAdminServiceImpl(userRepository, roleRepository, userRoleRepository, ENCODER);
        lenient().when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
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
        assertThat(user.getTokenVersion()).isEqualTo(1);
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
        assertThat(user.getTokenVersion()).isEqualTo(1);
        assertThat(dto.isActive()).isFalse();
        verify(userRepository, never()).countActiveUsersWithRoleExcluding(any(), any());
    }

    @Test
    void unlock_setsActive() {
        UserEntity user = givenUser(3L, "LOCKED", TEACHER_ROLE);

        service.changeStatus(3L, true, 1L);

        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void resetPassword_forcesChangeAndRevokesTokens() {
        UserEntity user = givenUser(3L, "ACTIVE", TEACHER_ROLE);

        service.resetPassword(3L, "Reset@5678", 1L);

        assertThat(ENCODER.matches("Reset@5678", user.getPasswordHash())).isTrue();
        assertThat(user.getMustChangePassword()).isEqualTo(1);
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void resetPassword_self_isRejected() {
        givenUser(1L, "ACTIVE", ADMIN_ROLE);

        assertThatThrownBy(() -> service.resetPassword(1L, "Reset@5678", 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("CANNOT_RESET_SELF");
    }

    @Test
    void assignRoles_removingAdminFromLastAdmin_isRejected() {
        givenUser(1L, "ACTIVE", ADMIN_ROLE);
        when(roleRepository.findByIdInAndIsDeleted(any(), any())).thenReturn(List.of(TEACHER_ROLE));
        when(userRepository.countActiveUsersWithRoleExcluding("ROLE_ADMIN", 1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.assignRoles(1L, List.of(2L)))
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
}
