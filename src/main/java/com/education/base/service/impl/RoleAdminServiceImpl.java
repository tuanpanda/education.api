package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FunctionCodes;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.TreeAssembler;
import com.education.base.dto.request.RoleCreateRequest;
import com.education.base.dto.request.RoleFilterRequest;
import com.education.base.dto.request.RolePermissionUpdateRequest;
import com.education.base.dto.request.RoleUpdateRequest;
import com.education.base.dto.response.FunctionOptionDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PermissionMenuNodeDto;
import com.education.base.dto.response.RolePermissionMatrixResponse;
import com.education.base.dto.response.RoleResponseDto;
import com.education.base.entity.FunctionEntity;
import com.education.base.entity.MenuEntity;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.RoleMenuPermissionEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FunctionRepository;
import com.education.base.repository.MenuRepository;
import com.education.base.repository.RoleMenuPermissionRepository;
import com.education.base.repository.RoleRepository;
import com.education.base.repository.UserRoleRepository;
import com.education.base.repository.spec.RoleSpecifications;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import com.education.base.service.RoleAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdminServiceImpl implements RoleAdminService {

    private static final Comparator<PermissionMenuNodeDto> NODE_ORDER =
            Comparator.comparing(PermissionMenuNodeDto::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(PermissionMenuNodeDto::getMenuId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final MenuRepository menuRepository;
    private final FunctionRepository functionRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleResponseDto> search(RoleFilterRequest filter) {
        RoleFilterRequest criteria = filter == null ? new RoleFilterRequest() : filter;
        Page<RoleEntity> page = roleRepository.findAll(RoleSpecifications.fromFilter(criteria),
                PageRequest.of(criteria.resolvePage() - 1, criteria.resolveSize(), Sort.by(Sort.Direction.ASC, "id")));
        Map<Long, Long> counts = userCounts(page.getContent().stream().map(RoleEntity::getId).toList());
        List<RoleResponseDto> content = page.getContent().stream()
                .map(role -> toDto(role, counts.getOrDefault(role.getId(), 0L)))
                .toList();
        return PageResponse.of(content, criteria.resolvePage(), criteria.resolveSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponseDto getById(Long id) {
        RoleEntity role = requireRole(id);
        return toDto(role, userCounts(List.of(id)).getOrDefault(id, 0L));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResponseDto create(RoleCreateRequest request) {
        String code = request.getRoleCode().trim();
        if (roleRepository.existsByRoleCode(code)) {
            throw new OracleBusinessException("ROLE_CODE_DUPLICATED", "Mã vai trò '" + code + "' đã tồn tại.");
        }
        RoleEntity role = RoleEntity.builder()
                .roleCode(code)
                .roleName(request.getRoleName().trim())
                .description(blankToNull(request.getDescription()))
                .status(Boolean.FALSE.equals(request.getActive())
                        ? DomainConstants.RECORD_STATUS_INACTIVE : DomainConstants.RECORD_STATUS_ACTIVE)
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .createdBy(SecurityUtils.currentUsername())
                .build();
        RoleEntity saved = roleRepository.save(role);
        log.info("Đã tạo vai trò id={}, code={}", saved.getId(), code);
        return toDto(saved, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResponseDto update(Long id, RoleUpdateRequest request) {
        RoleEntity role = requireRole(id);
        if (request.getActive() != null) {
            if (!request.getActive() && isAdminRole(role)) {
                throw new OracleBusinessException("ADMIN_ROLE_PROTECTED",
                        "Không thể ngừng hoạt động vai trò Quản trị viên hệ thống.");
            }
            role.setStatus(request.getActive()
                    ? DomainConstants.RECORD_STATUS_ACTIVE : DomainConstants.RECORD_STATUS_INACTIVE);
        }
        role.setRoleName(request.getRoleName().trim());
        role.setDescription(blankToNull(request.getDescription()));
        role.setUpdatedBy(SecurityUtils.currentUsername());
        RoleEntity saved = roleRepository.save(role);
        return toDto(saved, userCounts(List.of(id)).getOrDefault(id, 0L));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RoleEntity role = requireRole(id);
        if (isAdminRole(role)) {
            throw new OracleBusinessException("ADMIN_ROLE_PROTECTED", "Không thể xóa vai trò Quản trị viên hệ thống.");
        }
        long users = userCounts(List.of(id)).getOrDefault(id, 0L);
        if (users > 0) {
            throw new OracleBusinessException("ROLE_IN_USE",
                    "Vai trò đang được gán cho " + users + " người dùng, hãy gỡ vai trò trước khi xóa.");
        }
        List<RoleMenuPermissionEntity> permissions = roleMenuPermissionRepository.findByRoleId(id);
        if (!permissions.isEmpty()) {
            roleMenuPermissionRepository.deleteAll(permissions);
        }
        role.setIsDeleted(PersistenceFlags.DELETED);
        role.setUpdatedBy(SecurityUtils.currentUsername());
        roleRepository.save(role);
        log.info("Đã xóa mềm vai trò id={}, code={}", id, role.getRoleCode());
    }

    @Override
    @Transactional(readOnly = true)
    public RolePermissionMatrixResponse getPermissions(Long id) {
        RoleEntity role = requireRole(id);
        List<MenuEntity> menus = menuRepository.findByIsDeletedOrderBySortOrderAscIdAsc(PersistenceFlags.NOT_DELETED);
        Map<Long, List<FunctionEntity>> functionsByMenu = functionsByMenu();
        boolean admin = isAdminRole(role);

        Map<Long, Set<String>> granted = new HashMap<>();
        if (!admin) {
            for (RoleMenuPermissionEntity row : roleMenuPermissionRepository.findByRoleId(id)) {
                granted.put(row.getMenuId(), new LinkedHashSet<>(FunctionCodes.splitCsv(row.getAllowedFunctions())));
            }
        }

        Map<String, String> columnNames = new LinkedHashMap<>();
        List<PermissionMenuNodeDto> flat = new ArrayList<>();
        for (MenuEntity menu : menus) {
            List<FunctionEntity> functions = functionsByMenu.getOrDefault(menu.getId(), List.of());
            List<String> available = functions.stream()
                    .map(FunctionEntity::getFunctionCode)
                    .distinct()
                    .sorted(FunctionCodes.DISPLAY_ORDER)
                    .toList();
            for (FunctionEntity function : functions) {
                columnNames.putIfAbsent(function.getFunctionCode(), function.getFunctionName());
            }
            List<String> grantedCodes = admin
                    ? new ArrayList<>(available)
                    : available.stream().filter(granted.getOrDefault(menu.getId(), Set.of())::contains).toList();
            flat.add(PermissionMenuNodeDto.builder()
                    .menuId(menu.getId())
                    .parentId(menu.getParentId())
                    .menuCode(menu.getMenuCode())
                    .menuName(menu.getMenuName())
                    .menuType(menu.getMenuType())
                    .sortOrder(menu.getSortOrder())
                    .active(DomainConstants.RECORD_STATUS_ACTIVE.equals(menu.getStatus()))
                    .availableFunctions(new ArrayList<>(available))
                    .grantedFunctions(new ArrayList<>(grantedCodes))
                    .children(new ArrayList<>())
                    .build());
        }

        Set<String> columnCodes = new TreeSet<>(FunctionCodes.DISPLAY_ORDER);
        columnCodes.addAll(columnNames.keySet());
        List<FunctionOptionDto> columns = new ArrayList<>();
        for (String code : columnCodes) {
            columns.add(FunctionOptionDto.builder()
                    .code(code)
                    .name(FunctionCodes.DEFAULT_NAMES.containsKey(code)
                            ? FunctionCodes.defaultName(code) : columnNames.get(code))
                    .build());
        }

        return RolePermissionMatrixResponse.builder()
                .roleId(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .editable(!admin)
                .functions(columns)
                .menus(TreeAssembler.build(flat, PermissionMenuNodeDto::getMenuId, PermissionMenuNodeDto::getParentId,
                        PermissionMenuNodeDto::getChildren, NODE_ORDER))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RolePermissionMatrixResponse updatePermissions(Long id, RolePermissionUpdateRequest request) {
        RoleEntity role = requireRole(id);
        AuthUserPrincipal caller = SecurityUtils.currentUser().orElse(null);
        boolean callerIsAdmin = caller != null && caller.isAdmin();
        if (isAdminRole(role)) {
            if (!callerIsAdmin) {
                throw new ForbiddenException("ADMIN_ROLE_PROTECTED",
                        "Chỉ quản trị viên hệ thống mới được phân quyền cho vai trò Quản trị viên.");
            }
            throw new OracleBusinessException("ADMIN_ROLE_PROTECTED",
                    "Vai trò Quản trị viên hệ thống luôn có toàn quyền, không cần phân quyền.");
        }
        if (!callerIsAdmin && caller != null && caller.getRoles().contains(role.getRoleCode())) {
            throw new ForbiddenException("CANNOT_EDIT_OWN_ROLE",
                    "Không thể tự phân quyền cho vai trò mà bạn đang được gán.");
        }
        Map<Long, MenuEntity> menus = new LinkedHashMap<>();
        for (MenuEntity menu : menuRepository.findByIsDeletedOrderBySortOrderAscIdAsc(PersistenceFlags.NOT_DELETED)) {
            menus.put(menu.getId(), menu);
        }
        Map<Long, Set<String>> available = new HashMap<>();
        functionsByMenu().forEach((menuId, functions) -> available.put(menuId,
                new LinkedHashSet<>(functions.stream().map(FunctionEntity::getFunctionCode).toList())));

        Map<Long, Set<String>> target = new LinkedHashMap<>();
        List<RolePermissionUpdateRequest.MenuPermission> items =
                request.getPermissions() == null ? List.of() : request.getPermissions();
        for (RolePermissionUpdateRequest.MenuPermission item : items) {
            MenuEntity menu = menus.get(item.getMenuId());
            if (menu == null) {
                throw new OracleBusinessException("MENU_NOT_FOUND",
                        "Không tìm thấy menu với ID: " + item.getMenuId());
            }
            Set<String> allowed = available.getOrDefault(menu.getId(), Set.of());
            for (String code : item.getFunctions() == null ? List.<String>of() : item.getFunctions()) {
                if (!allowed.contains(code)) {
                    throw new OracleBusinessException("INVALID_FUNCTION",
                            "Chức năng '" + code + "' không thuộc menu '" + menu.getMenuName() + "'.");
                }
                target.computeIfAbsent(menu.getId(), key -> new LinkedHashSet<>()).add(code);
            }
        }

        Map<Long, RoleMenuPermissionEntity> existing = new HashMap<>();
        for (RoleMenuPermissionEntity row : roleMenuPermissionRepository.findByRoleId(id)) {
            existing.put(row.getMenuId(), row);
        }
        if (!callerIsAdmin) {
            requireGrantsHeldByCaller(caller, target, existing, menus);
        }

        // Có quyền trên menu con thì menu cha phải có VIEW để hiện trên sidebar.
        for (Long menuId : new ArrayList<>(target.keySet())) {
            Long parentId = menus.get(menuId).getParentId();
            while (parentId != null && menus.containsKey(parentId)) {
                if (available.getOrDefault(parentId, Set.of()).contains(FunctionCodes.VIEW)) {
                    target.computeIfAbsent(parentId, key -> new LinkedHashSet<>()).add(FunctionCodes.VIEW);
                }
                parentId = menus.get(parentId).getParentId();
            }
        }

        String actor = SecurityUtils.currentUsername();
        List<RoleMenuPermissionEntity> toSave = new ArrayList<>();
        List<RoleMenuPermissionEntity> toDelete = new ArrayList<>();
        for (Map.Entry<Long, RoleMenuPermissionEntity> entry : existing.entrySet()) {
            if (!target.containsKey(entry.getKey())) {
                toDelete.add(entry.getValue());
            }
        }
        target.forEach((menuId, codes) -> {
            String csv = FunctionCodes.joinCsv(codes);
            RoleMenuPermissionEntity row = existing.get(menuId);
            if (row == null) {
                toSave.add(RoleMenuPermissionEntity.builder()
                        .roleId(id)
                        .menuId(menuId)
                        .allowedFunctions(csv)
                        .createdBy(actor)
                        .build());
            } else if (!csv.equals(row.getAllowedFunctions())) {
                row.setAllowedFunctions(csv);
                row.setUpdatedBy(actor);
                toSave.add(row);
            }
        });
        if (!toDelete.isEmpty()) {
            roleMenuPermissionRepository.deleteAll(toDelete);
        }
        if (!toSave.isEmpty()) {
            roleMenuPermissionRepository.saveAll(toSave);
        }
        log.info("Cập nhật phân quyền vai trò id={}: {} menu được cấp quyền, {} menu bị thu hồi",
                id, target.size(), toDelete.size());
        return getPermissions(id);
    }

    /**
     * Người không phải quản trị viên chỉ được CẤP THÊM những quyền chính mình đang có. Quyền vai trò đã có từ trước
     * được giữ nguyên hoặc thu hồi tự do; VIEW của menu cha được thêm tự động (chỉ để hiện sidebar) không xét ở đây.
     */
    private static void requireGrantsHeldByCaller(AuthUserPrincipal caller, Map<Long, Set<String>> target,
                                                  Map<Long, RoleMenuPermissionEntity> existing,
                                                  Map<Long, MenuEntity> menus) {
        Set<String> held = caller == null || caller.getPermissions() == null ? Set.of() : caller.getPermissions();
        for (Map.Entry<Long, Set<String>> entry : target.entrySet()) {
            RoleMenuPermissionEntity row = existing.get(entry.getKey());
            Set<String> alreadyGranted = row == null
                    ? Set.of() : new LinkedHashSet<>(FunctionCodes.splitCsv(row.getAllowedFunctions()));
            MenuEntity menu = menus.get(entry.getKey());
            for (String code : entry.getValue()) {
                if (!alreadyGranted.contains(code) && !held.contains(Permissions.of(menu.getMenuCode(), code))) {
                    throw new ForbiddenException("GRANT_NOT_HELD",
                            "Bạn không thể cấp quyền '" + code + "' trên menu '" + menu.getMenuName()
                                    + "' vì chính bạn không có quyền này.");
                }
            }
        }
    }

    private Map<Long, List<FunctionEntity>> functionsByMenu() {
        Map<Long, List<FunctionEntity>> result = new HashMap<>();
        for (FunctionEntity function : functionRepository.findByIsDeletedOrderByIdAsc(PersistenceFlags.NOT_DELETED)) {
            result.computeIfAbsent(function.getMenuId(), key -> new ArrayList<>()).add(function);
        }
        return result;
    }

    private Map<Long, Long> userCounts(List<Long> roleIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (roleIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : userRoleRepository.countActiveUsersByRoleIds(roleIds)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private RoleEntity requireRole(Long id) {
        return roleRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException("ROLE_NOT_FOUND",
                        "Không tìm thấy vai trò với ID: " + id));
    }

    private static boolean isAdminRole(RoleEntity role) {
        return Permissions.ADMIN_ROLE.equals(role.getRoleCode());
    }

    private static RoleResponseDto toDto(RoleEntity role, long userCount) {
        return RoleResponseDto.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .status(role.getStatus())
                .active(DomainConstants.RECORD_STATUS_ACTIVE.equals(role.getStatus()))
                .system(isAdminRole(role))
                .userCount(userCount)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
