package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.UserCreateRequest;
import com.education.base.dto.request.UserFilterRequest;
import com.education.base.dto.request.UserUpdateRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.RoleSummaryDto;
import com.education.base.dto.response.UserResponseDto;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.UserEntity;
import com.education.base.entity.UserRoleEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.RoleRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.UserRoleRepository;
import com.education.base.repository.spec.UserSpecifications;
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import com.education.base.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponseDto> search(UserFilterRequest filter) {
        UserFilterRequest criteria = filter == null ? new UserFilterRequest() : filter;
        Page<UserEntity> page = userRepository.findAll(
                UserSpecifications.fromFilter(criteria),
                PageRequest.of(criteria.resolvePage() - 1, criteria.resolveSize(), Sort.by(Sort.Direction.DESC, "id")));
        List<Long> userIds = page.getContent().stream().map(UserEntity::getId).toList();
        Map<Long, List<RoleSummaryDto>> rolesByUser = rolesByUser(userIds);
        List<UserResponseDto> content = new ArrayList<>();
        for (UserEntity user : page.getContent()) {
            content.add(toDto(user, rolesByUser.getOrDefault(user.getId(), List.of())));
        }
        return PageResponse.of(content, criteria.resolvePage(), criteria.resolveSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getById(Long id) {
        return toDto(requireUser(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponseDto create(UserCreateRequest request) {
        String username = request.getUsername().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new OracleBusinessException("USERNAME_DUPLICATED", "Tên đăng nhập '" + username + "' đã tồn tại.");
        }
        List<RoleEntity> roles = requireAssignableRoles(request.getRoleIds());
        String actor = SecurityUtils.currentUsername();

        UserEntity user = UserEntity.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .email(blankToNull(request.getEmail()))
                .phone(blankToNull(request.getPhone()))
                .status(request.getStatus() == null || request.getStatus().isBlank()
                        ? DomainConstants.USER_STATUS_ACTIVE : request.getStatus())
                .mustChangePassword(Boolean.FALSE.equals(request.getMustChangePassword()) ? 0 : 1)
                .tokenVersion(0)
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .passwordChangedAt(LocalDateTime.now())
                .createdBy(actor)
                .build();
        UserEntity saved = userRepository.save(user);
        replaceRoles(saved.getId(), roles, actor);
        log.info("Đã tạo người dùng id={}, username={}, roles={}", saved.getId(), username,
                roles.stream().map(RoleEntity::getRoleCode).toList());
        return toDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponseDto update(Long id, UserUpdateRequest request) {
        UserEntity user = requireUser(id);
        user.setFullName(request.getFullName().trim());
        user.setEmail(blankToNull(request.getEmail()));
        user.setPhone(blankToNull(request.getPhone()));
        user.setUpdatedBy(SecurityUtils.currentUsername());
        return toDto(userRepository.save(user));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long currentUserId) {
        UserEntity user = requireUser(id);
        if (Objects.equals(id, currentUserId)) {
            throw new OracleBusinessException("CANNOT_DELETE_SELF", "Không thể xóa tài khoản đang đăng nhập.");
        }
        guardLastAdmin(user, "xóa");
        user.setIsDeleted(PersistenceFlags.DELETED);
        user.setTokenVersion(nextVersion(user));
        user.setUpdatedBy(SecurityUtils.currentUsername());
        userRepository.save(user);
        log.info("Đã xóa mềm người dùng id={}, username={}", id, user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponseDto changeStatus(Long id, boolean active, Long currentUserId) {
        UserEntity user = requireUser(id);
        if (!active) {
            if (Objects.equals(id, currentUserId)) {
                throw new OracleBusinessException("CANNOT_LOCK_SELF", "Không thể khóa tài khoản đang đăng nhập.");
            }
            guardLastAdmin(user, "khóa");
            user.setStatus(DomainConstants.USER_STATUS_LOCKED);
            user.setTokenVersion(nextVersion(user));
        } else {
            user.setStatus(DomainConstants.USER_STATUS_ACTIVE);
        }
        user.setUpdatedBy(SecurityUtils.currentUsername());
        log.info("Đổi trạng thái người dùng id={} -> {}", id, user.getStatus());
        return toDto(userRepository.save(user));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword, Long currentUserId) {
        UserEntity user = requireUser(id);
        if (Objects.equals(id, currentUserId)) {
            throw new OracleBusinessException("CANNOT_RESET_SELF",
                    "Hãy dùng chức năng Đổi mật khẩu cho tài khoản đang đăng nhập.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(1);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setTokenVersion(nextVersion(user));
        user.setUpdatedBy(SecurityUtils.currentUsername());
        userRepository.save(user);
        log.info("Đã đặt lại mật khẩu cho người dùng id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponseDto assignRoles(Long id, List<Long> roleIds) {
        UserEntity user = requireUser(id);
        List<RoleEntity> roles = requireAssignableRoles(roleIds);
        boolean keepsAdmin = roles.stream().anyMatch(role -> Permissions.ADMIN_ROLE.equals(role.getRoleCode()));
        if (!keepsAdmin) {
            guardLastAdmin(user, "gỡ vai trò quản trị của");
        }
        replaceRoles(user.getId(), roles, SecurityUtils.currentUsername());
        log.info("Gán vai trò cho người dùng id={}: {}", id, roles.stream().map(RoleEntity::getRoleCode).toList());
        return toDto(user);
    }

    private UserEntity requireUser(Long id) {
        return userRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException("USER_NOT_FOUND",
                        "Không tìm thấy người dùng với ID: " + id));
    }

    private List<RoleEntity> requireAssignableRoles(List<Long> roleIds) {
        Set<Long> ids = new LinkedHashSet<>(roleIds == null ? List.of() : roleIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<RoleEntity> roles = roleRepository.findByIdInAndIsDeleted(ids, PersistenceFlags.NOT_DELETED);
        if (roles.size() != ids.size()) {
            throw new OracleBusinessException("ROLE_NOT_FOUND", "Có vai trò không tồn tại hoặc đã bị xóa.");
        }
        for (RoleEntity role : roles) {
            if (!DomainConstants.RECORD_STATUS_ACTIVE.equals(role.getStatus())) {
                throw new OracleBusinessException("ROLE_INACTIVE",
                        "Vai trò '" + role.getRoleName() + "' đang ngừng hoạt động, không thể gán.");
            }
        }
        return roles;
    }

    /**
     * Chặn thao tác làm hệ thống mất quản trị viên hoạt động cuối cùng.
     */
    private void guardLastAdmin(UserEntity user, String action) {
        if (!DomainConstants.USER_STATUS_ACTIVE.equals(user.getStatus()) || !hasAdminRole(user.getId())) {
            return;
        }
        long otherAdmins = userRepository.countActiveUsersWithRoleExcluding(Permissions.ADMIN_ROLE, user.getId());
        if (otherAdmins == 0) {
            throw new OracleBusinessException("LAST_ADMIN",
                    "Không thể " + action + " quản trị viên cuối cùng của hệ thống.");
        }
    }

    private boolean hasAdminRole(Long userId) {
        return userRoleRepository.findWithRoleByUserIdIn(List.of(userId)).stream()
                .map(UserRoleEntity::getRole)
                .anyMatch(role -> role != null
                        && Objects.equals(role.getIsDeleted(), PersistenceFlags.NOT_DELETED)
                        && Permissions.ADMIN_ROLE.equals(role.getRoleCode()));
    }

    private void replaceRoles(Long userId, List<RoleEntity> roles, String actor) {
        Set<Long> target = new LinkedHashSet<>();
        roles.forEach(role -> target.add(role.getId()));
        List<UserRoleEntity> current = userRoleRepository.findByUserId(userId);
        List<UserRoleEntity> toRemove = new ArrayList<>();
        Set<Long> existing = new LinkedHashSet<>();
        for (UserRoleEntity userRole : current) {
            if (target.contains(userRole.getRoleId())) {
                existing.add(userRole.getRoleId());
            } else {
                toRemove.add(userRole);
            }
        }
        if (!toRemove.isEmpty()) {
            userRoleRepository.deleteAll(toRemove);
        }
        List<UserRoleEntity> toAdd = new ArrayList<>();
        for (Long roleId : target) {
            if (!existing.contains(roleId)) {
                toAdd.add(UserRoleEntity.builder()
                        .userId(userId)
                        .roleId(roleId)
                        .assignedAt(LocalDateTime.now())
                        .assignedBy(actor)
                        .build());
            }
        }
        if (!toAdd.isEmpty()) {
            userRoleRepository.saveAll(toAdd);
        }
    }

    private Map<Long, List<RoleSummaryDto>> rolesByUser(List<Long> userIds) {
        Map<Long, List<RoleSummaryDto>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        for (UserRoleEntity userRole : userRoleRepository.findWithRoleByUserIdIn(userIds)) {
            RoleEntity role = userRole.getRole();
            if (role == null || !Objects.equals(role.getIsDeleted(), PersistenceFlags.NOT_DELETED)) {
                continue;
            }
            result.computeIfAbsent(userRole.getUserId(), key -> new ArrayList<>())
                    .add(RoleSummaryDto.builder()
                            .id(role.getId())
                            .roleCode(role.getRoleCode())
                            .roleName(role.getRoleName())
                            .build());
        }
        result.values().forEach(list -> list.sort(Comparator.comparing(RoleSummaryDto::getRoleCode)));
        return result;
    }

    private UserResponseDto toDto(UserEntity user) {
        return toDto(user, rolesByUser(List.of(user.getId())).getOrDefault(user.getId(), List.of()));
    }

    private static UserResponseDto toDto(UserEntity user, List<RoleSummaryDto> roles) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .active(DomainConstants.USER_STATUS_ACTIVE.equals(user.getStatus()))
                .mustChangePassword(Integer.valueOf(1).equals(user.getMustChangePassword()))
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .updatedBy(user.getUpdatedBy())
                .roles(new ArrayList<>(roles))
                .build();
    }

    private static int nextVersion(UserEntity user) {
        return (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
