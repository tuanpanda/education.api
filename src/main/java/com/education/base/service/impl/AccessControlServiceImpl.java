package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FunctionCodes;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.TreeAssembler;
import com.education.base.dto.response.MenuItemResponseDto;
import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.entity.FunctionEntity;
import com.education.base.entity.MenuEntity;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.RoleMenuPermissionEntity;
import com.education.base.entity.UserEntity;
import com.education.base.entity.UserRoleEntity;
import com.education.base.entity.UserStudentLinkEntity;
import com.education.base.exception.UnauthorizedException;
import com.education.base.repository.FunctionRepository;
import com.education.base.repository.MenuRepository;
import com.education.base.repository.RoleMenuPermissionRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.UserRoleRepository;
import com.education.base.repository.UserStudentLinkRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.security.UserType;
import com.education.base.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessControlServiceImpl implements AccessControlService {

    private static final Comparator<MenuItemResponseDto> MENU_ORDER =
            Comparator.comparing(MenuItemResponseDto::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(MenuItemResponseDto::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final MenuRepository menuRepository;
    private final FunctionRepository functionRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;
    private final UserStudentLinkRepository userStudentLinkRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthUserPrincipal> loadActivePrincipal(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdAndIsDeleted(userId, PersistenceFlags.NOT_DELETED)
                .filter(user -> DomainConstants.USER_STATUS_ACTIVE.equals(user.getStatus()))
                .filter(this::hasKnownUserType)
                .map(this::buildPrincipal);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Tài khoản không phải nhân viên ({@code USER_TYPE <> STAFF}) KHÔNG nhận quyền menu nào (kể cả khi lỡ được gán
     * vai trò nhân viên); tài khoản học sinh được gắn {@code studentId} từ liên kết {@code SELF} đang hoạt động.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthUserPrincipal buildPrincipal(UserEntity user) {
        UserType userType = UserType.fromDb(user.getUserType())
                .orElseThrow(() -> new UnauthorizedException("ACCOUNT_INACTIVE",
                        "Tài khoản chưa được kích hoạt hoặc đã ngừng hoạt động."));
        List<RoleEntity> roles = activeRoles(user.getId());
        Set<String> permissions = userType == UserType.STAFF ? resolvePermissions(roles) : new TreeSet<>();
        Long studentId = userType == UserType.STUDENT
                ? userStudentLinkRepository.findActiveSelfLinkByUserId(user.getId())
                        .map(UserStudentLinkEntity::getStudentId)
                        .orElse(null)
                : null;
        return AuthUserPrincipal.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .roles(roles.stream().map(RoleEntity::getRoleCode).toList())
                .permissions(permissions)
                .mustChangePassword(Integer.valueOf(1).equals(user.getMustChangePassword()))
                .tokenVersion(user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                .userType(userType)
                .studentId(studentId)
                .build();
    }

    private boolean hasKnownUserType(UserEntity user) {
        if (UserType.fromDb(user.getUserType()).isPresent()) {
            return true;
        }
        log.warn("Từ chối phiên của userId={}: USER_TYPE không hợp lệ '{}'", user.getId(), user.getUserType());
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> resolvePermissions(Collection<RoleEntity> roles) {
        if (roles == null || roles.isEmpty()) {
            return new TreeSet<>();
        }
        Map<Long, MenuEntity> menus = activeMenus(false);
        Map<Long, List<String>> functionsByMenu = functionsByMenu(menus.keySet());
        Map<Long, Set<String>> granted = grantedFunctions(roles, functionsByMenu);

        Set<String> permissions = new TreeSet<>();
        granted.forEach((menuId, codes) -> {
            MenuEntity menu = menus.get(menuId);
            for (String code : codes) {
                permissions.add(Permissions.of(menu.getMenuCode(), code));
            }
        });
        return permissions;
    }

    @Override
    @Transactional(readOnly = true)
    public UserNavigationResponseDto getNavigation(AuthUserPrincipal principal) {
        Map<Long, MenuEntity> menus = activeMenus(true);
        Map<String, List<String>> allowedByMenuCode = allowedByMenuCode(principal.getPermissions());

        Set<Long> visible = new LinkedHashSet<>();
        for (MenuEntity menu : menus.values()) {
            List<String> allowed = allowedByMenuCode.get(menu.getMenuCode());
            if (allowed != null && allowed.contains(FunctionCodes.VIEW)) {
                visible.add(menu.getId());
            }
        }
        // Luôn hiển thị menu cha chứa menu con được phép xem để cây sidebar không bị đứt.
        for (Long id : new ArrayList<>(visible)) {
            Long parentId = menus.get(id).getParentId();
            while (parentId != null && menus.containsKey(parentId) && visible.add(parentId)) {
                parentId = menus.get(parentId).getParentId();
            }
        }

        List<MenuItemResponseDto> flat = new ArrayList<>();
        for (MenuEntity menu : menus.values()) {
            if (!visible.contains(menu.getId())) {
                continue;
            }
            flat.add(MenuItemResponseDto.builder()
                    .id(menu.getId())
                    .parentId(menu.getParentId())
                    .menuCode(menu.getMenuCode())
                    .menuName(menu.getMenuName())
                    .menuType(menu.getMenuType())
                    .path(menu.getPath())
                    .icon(menu.getIcon())
                    .sortOrder(menu.getSortOrder())
                    .allowedFunctions(new ArrayList<>(allowedByMenuCode.getOrDefault(menu.getMenuCode(), List.of())))
                    .children(new ArrayList<>())
                    .build());
        }
        List<MenuItemResponseDto> tree = TreeAssembler.build(flat, MenuItemResponseDto::getId,
                MenuItemResponseDto::getParentId, MenuItemResponseDto::getChildren, MENU_ORDER);
        return UserNavigationResponseDto.builder()
                .menus(tree)
                .permissions(new LinkedHashSet<>(principal.getPermissions()))
                .build();
    }

    private List<RoleEntity> activeRoles(Long userId) {
        List<RoleEntity> roles = new ArrayList<>();
        for (UserRoleEntity userRole : userRoleRepository.findWithRoleByUserIdIn(List.of(userId))) {
            RoleEntity role = userRole.getRole();
            if (role != null
                    && Objects.equals(role.getIsDeleted(), PersistenceFlags.NOT_DELETED)
                    && DomainConstants.RECORD_STATUS_ACTIVE.equals(role.getStatus())) {
                roles.add(role);
            }
        }
        roles.sort(Comparator.comparing(RoleEntity::getRoleCode));
        return roles;
    }

    /**
     * Menu chưa xóa và đang {@code ACTIVE}; {@code excludeHidden} loại thêm menu ẩn khỏi sidebar.
     */
    private Map<Long, MenuEntity> activeMenus(boolean excludeHidden) {
        Map<Long, MenuEntity> menus = new LinkedHashMap<>();
        for (MenuEntity menu : menuRepository.findByIsDeletedOrderBySortOrderAscIdAsc(PersistenceFlags.NOT_DELETED)) {
            if (!DomainConstants.RECORD_STATUS_ACTIVE.equals(menu.getStatus())) {
                continue;
            }
            if (excludeHidden && Integer.valueOf(1).equals(menu.getIsHidden())) {
                continue;
            }
            menus.put(menu.getId(), menu);
        }
        return menus;
    }

    private Map<Long, List<String>> functionsByMenu(Set<Long> menuIds) {
        Map<Long, List<String>> result = new HashMap<>();
        for (FunctionEntity function : functionRepository.findByIsDeletedOrderByIdAsc(PersistenceFlags.NOT_DELETED)) {
            if (menuIds.contains(function.getMenuId())) {
                result.computeIfAbsent(function.getMenuId(), key -> new ArrayList<>()).add(function.getFunctionCode());
            }
        }
        return result;
    }

    private Map<Long, Set<String>> grantedFunctions(Collection<RoleEntity> roles,
                                                    Map<Long, List<String>> functionsByMenu) {
        Map<Long, Set<String>> granted = new HashMap<>();
        boolean admin = roles.stream().anyMatch(role -> Permissions.ADMIN_ROLE.equals(role.getRoleCode()));
        if (admin) {
            functionsByMenu.forEach((menuId, codes) -> granted.put(menuId, new LinkedHashSet<>(codes)));
            return granted;
        }
        List<Long> roleIds = roles.stream().map(RoleEntity::getId).toList();
        for (RoleMenuPermissionEntity row : roleMenuPermissionRepository.findByRoleIdIn(roleIds)) {
            List<String> available = functionsByMenu.get(row.getMenuId());
            if (available == null) {
                continue;
            }
            for (String code : FunctionCodes.splitCsv(row.getAllowedFunctions())) {
                if (available.contains(code)) {
                    granted.computeIfAbsent(row.getMenuId(), key -> new LinkedHashSet<>()).add(code);
                }
            }
        }
        return granted;
    }

    private static Map<String, List<String>> allowedByMenuCode(Set<String> permissions) {
        Map<String, List<String>> result = new HashMap<>();
        if (permissions == null) {
            return result;
        }
        for (String permission : permissions) {
            int separator = permission.lastIndexOf(Permissions.SEPARATOR);
            if (separator <= 0) {
                continue;
            }
            result.computeIfAbsent(permission.substring(0, separator), key -> new ArrayList<>())
                    .add(permission.substring(separator + 1));
        }
        result.values().forEach(list -> list.sort(FunctionCodes.DISPLAY_ORDER));
        return result;
    }
}
