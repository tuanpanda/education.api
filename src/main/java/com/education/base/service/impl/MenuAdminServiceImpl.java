package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FunctionCodes;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.TreeAssembler;
import com.education.base.dto.request.MenuReorderRequest;
import com.education.base.dto.request.MenuUpsertRequest;
import com.education.base.dto.response.AdminMenuResponseDto;
import com.education.base.dto.response.FunctionOptionDto;
import com.education.base.entity.FunctionEntity;
import com.education.base.entity.MenuEntity;
import com.education.base.entity.RoleMenuPermissionEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FunctionRepository;
import com.education.base.repository.MenuRepository;
import com.education.base.repository.RoleMenuPermissionRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.MenuAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuAdminServiceImpl implements MenuAdminService {

    /** Menu quản trị hệ thống: không cho xóa để tránh tự khóa màn hình quản trị. */
    static final Set<String> PROTECTED_MENU_CODES =
            Set.of("DIR_SYSTEM", "MENU_USER_LIST", "MENU_ROLE_LIST", "MENU_MENU_CONFIG");

    private static final Comparator<AdminMenuResponseDto> MENU_ORDER =
            Comparator.comparing(AdminMenuResponseDto::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(AdminMenuResponseDto::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final MenuRepository menuRepository;
    private final FunctionRepository functionRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminMenuResponseDto> getTree() {
        Map<Long, List<FunctionEntity>> functions = functionsByMenu();
        List<AdminMenuResponseDto> flat = new ArrayList<>();
        for (MenuEntity menu : menuRepository.findByIsDeletedOrderBySortOrderAscIdAsc(PersistenceFlags.NOT_DELETED)) {
            flat.add(toDto(menu, functions.getOrDefault(menu.getId(), List.of())));
        }
        return TreeAssembler.build(flat, AdminMenuResponseDto::getId, AdminMenuResponseDto::getParentId,
                AdminMenuResponseDto::getChildren, MENU_ORDER);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminMenuResponseDto getById(Long id) {
        MenuEntity menu = requireMenu(id);
        return toDto(menu, functionRepository.findByMenuIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMenuResponseDto create(MenuUpsertRequest request) {
        String code = request.getCode().trim();
        if (menuRepository.existsByMenuCode(code)) {
            throw new OracleBusinessException("MENU_CODE_DUPLICATED", "Mã menu '" + code + "' đã tồn tại.");
        }
        if (request.getParentId() != null) {
            requireMenu(request.getParentId());
        }
        String path = blankToNull(request.getPath());
        String menuType = resolveMenuType(request.getMenuType(), path);
        MenuEntity menu = MenuEntity.builder()
                .parentId(request.getParentId())
                .menuCode(code)
                .menuName(request.getName().trim())
                .menuType(menuType)
                .path(path)
                .icon(blankToNull(request.getIcon()))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSortOrder(request.getParentId()))
                .isHidden(Boolean.TRUE.equals(request.getHidden()) ? 1 : 0)
                .status(Boolean.FALSE.equals(request.getActive())
                        ? DomainConstants.RECORD_STATUS_INACTIVE : DomainConstants.RECORD_STATUS_ACTIVE)
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .createdBy(SecurityUtils.currentUsername())
                .build();
        MenuEntity saved = menuRepository.save(menu);
        List<String> codes = request.getFunctionCodes() == null
                ? (DomainConstants.MENU_TYPE_DIR.equals(menuType) ? FunctionCodes.DEFAULT_FOR_DIR : FunctionCodes.DEFAULT_FOR_MENU)
                : request.getFunctionCodes();
        syncFunctions(saved.getId(), codes);
        log.info("Đã tạo menu id={}, code={}", saved.getId(), code);
        return getById(saved.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMenuResponseDto update(Long id, MenuUpsertRequest request) {
        MenuEntity menu = requireMenu(id);
        if (!menu.getMenuCode().equals(request.getCode().trim())) {
            throw new OracleBusinessException("MENU_CODE_IMMUTABLE",
                    "Không được đổi mã menu (mã menu gắn với mã quyền đang dùng).");
        }
        Map<Long, MenuEntity> all = allMenus();
        validateParent(id, request.getParentId(), all);
        String path = blankToNull(request.getPath());
        menu.setParentId(request.getParentId());
        menu.setMenuName(request.getName().trim());
        menu.setMenuType(resolveMenuType(request.getMenuType(), path));
        menu.setPath(path);
        menu.setIcon(blankToNull(request.getIcon()));
        if (request.getSortOrder() != null) {
            menu.setSortOrder(request.getSortOrder());
        }
        if (request.getActive() != null) {
            menu.setStatus(request.getActive()
                    ? DomainConstants.RECORD_STATUS_ACTIVE : DomainConstants.RECORD_STATUS_INACTIVE);
        }
        if (request.getHidden() != null) {
            menu.setIsHidden(request.getHidden() ? 1 : 0);
        }
        menu.setUpdatedBy(SecurityUtils.currentUsername());
        menuRepository.save(menu);
        if (request.getFunctionCodes() != null) {
            syncFunctions(id, request.getFunctionCodes());
        }
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MenuEntity menu = requireMenu(id);
        if (PROTECTED_MENU_CODES.contains(menu.getMenuCode())) {
            throw new OracleBusinessException("MENU_PROTECTED", "Không thể xóa menu quản trị hệ thống.");
        }
        if (menuRepository.existsByParentIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)) {
            throw new OracleBusinessException("MENU_HAS_CHILDREN", "Menu đang có menu con, hãy xóa hoặc chuyển menu con trước.");
        }
        List<FunctionEntity> functions = functionRepository.findByMenuIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED);
        functions.forEach(function -> function.setIsDeleted(PersistenceFlags.DELETED));
        functionRepository.saveAll(functions);
        List<RoleMenuPermissionEntity> permissions = roleMenuPermissionRepository.findByMenuId(id);
        if (!permissions.isEmpty()) {
            roleMenuPermissionRepository.deleteAll(permissions);
        }
        menu.setIsDeleted(PersistenceFlags.DELETED);
        menu.setUpdatedBy(SecurityUtils.currentUsername());
        menuRepository.save(menu);
        log.info("Đã xóa mềm menu id={}, code={}", id, menu.getMenuCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdminMenuResponseDto> reorder(MenuReorderRequest request) {
        Map<Long, MenuEntity> all = allMenus();
        Map<Long, Long> newParents = new HashMap<>();
        all.values().forEach(menu -> newParents.put(menu.getId(), menu.getParentId()));

        Set<Long> seen = new HashSet<>();
        for (MenuReorderRequest.Item item : request.getItems()) {
            if (!all.containsKey(item.getId())) {
                throw new OracleBusinessException("MENU_NOT_FOUND", "Không tìm thấy menu với ID: " + item.getId());
            }
            if (!seen.add(item.getId())) {
                throw new OracleBusinessException("MENU_DUPLICATED", "Menu ID " + item.getId() + " bị lặp trong danh sách.");
            }
            if (item.getParentId() != null && !all.containsKey(item.getParentId())) {
                throw new OracleBusinessException("MENU_NOT_FOUND",
                        "Không tìm thấy menu cha với ID: " + item.getParentId());
            }
            newParents.put(item.getId(), item.getParentId());
        }
        for (Long id : seen) {
            ensureNoCycle(id, newParents);
        }

        String actor = SecurityUtils.currentUsername();
        List<MenuEntity> changed = new ArrayList<>();
        for (MenuReorderRequest.Item item : request.getItems()) {
            MenuEntity menu = all.get(item.getId());
            if (!Objects.equals(menu.getParentId(), item.getParentId())
                    || !Objects.equals(menu.getSortOrder(), item.getSortOrder())) {
                menu.setParentId(item.getParentId());
                menu.setSortOrder(item.getSortOrder());
                menu.setUpdatedBy(actor);
                changed.add(menu);
            }
        }
        menuRepository.saveAll(changed);
        log.info("Sắp xếp lại {} menu", changed.size());
        return getTree();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FunctionOptionDto> listFunctions() {
        Set<Long> activeMenuIds = allMenus().keySet();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Set<Long>> menusByCode = new HashMap<>();
        for (FunctionEntity function : functionRepository.findByIsDeletedOrderByIdAsc(PersistenceFlags.NOT_DELETED)) {
            if (!activeMenuIds.contains(function.getMenuId())) {
                continue;
            }
            names.putIfAbsent(function.getFunctionCode(), function.getFunctionName());
            menusByCode.computeIfAbsent(function.getFunctionCode(), key -> new HashSet<>()).add(function.getMenuId());
        }
        Map<String, FunctionOptionDto> catalog = new TreeMap<>(FunctionCodes.DISPLAY_ORDER);
        for (String code : FunctionCodes.STANDARD) {
            catalog.put(code, FunctionOptionDto.builder().code(code).name(FunctionCodes.defaultName(code)).menuCount(0).build());
        }
        names.forEach((code, name) -> catalog.put(code, FunctionOptionDto.builder()
                .code(code)
                .name(FunctionCodes.DEFAULT_NAMES.containsKey(code) ? FunctionCodes.defaultName(code) : name)
                .menuCount(menusByCode.getOrDefault(code, Set.of()).size())
                .build()));
        return new ArrayList<>(catalog.values());
    }

    /**
     * Đồng bộ chức năng của menu: thêm mới / khôi phục mã được chọn, xóa mềm mã bị bỏ và gỡ khỏi
     * {@code ALLOWED_FUNCTIONS} của các vai trò. {@code VIEW} luôn được giữ.
     */
    private void syncFunctions(Long menuId, List<String> requestedCodes) {
        Set<String> target = new LinkedHashSet<>();
        target.add(FunctionCodes.VIEW);
        for (String code : requestedCodes) {
            if (code != null && !code.isBlank()) {
                target.add(code.trim());
            }
        }
        Map<String, FunctionEntity> existing = new HashMap<>();
        for (FunctionEntity function : functionRepository.findByMenuId(menuId)) {
            existing.put(function.getFunctionCode(), function);
        }
        List<FunctionEntity> toSave = new ArrayList<>();
        for (String code : target) {
            FunctionEntity function = existing.get(code);
            if (function == null) {
                toSave.add(FunctionEntity.builder()
                        .menuId(menuId)
                        .functionCode(code)
                        .functionName(FunctionCodes.defaultName(code))
                        .isDeleted(PersistenceFlags.NOT_DELETED)
                        .build());
            } else if (!Objects.equals(function.getIsDeleted(), PersistenceFlags.NOT_DELETED)) {
                function.setIsDeleted(PersistenceFlags.NOT_DELETED);
                toSave.add(function);
            }
        }
        for (FunctionEntity function : existing.values()) {
            if (!target.contains(function.getFunctionCode())
                    && Objects.equals(function.getIsDeleted(), PersistenceFlags.NOT_DELETED)) {
                function.setIsDeleted(PersistenceFlags.DELETED);
                toSave.add(function);
            }
        }
        if (!toSave.isEmpty()) {
            functionRepository.saveAll(toSave);
        }

        List<RoleMenuPermissionEntity> changedPermissions = new ArrayList<>();
        for (RoleMenuPermissionEntity row : roleMenuPermissionRepository.findByMenuId(menuId)) {
            List<String> kept = FunctionCodes.splitCsv(row.getAllowedFunctions()).stream()
                    .filter(target::contains)
                    .toList();
            String csv = FunctionCodes.joinCsv(kept);
            if (!csv.equals(row.getAllowedFunctions())) {
                row.setAllowedFunctions(csv);
                changedPermissions.add(row);
            }
        }
        if (!changedPermissions.isEmpty()) {
            roleMenuPermissionRepository.saveAll(changedPermissions);
        }
    }

    private void validateParent(Long id, Long parentId, Map<Long, MenuEntity> all) {
        if (parentId == null) {
            return;
        }
        if (!all.containsKey(parentId)) {
            throw new OracleBusinessException("MENU_NOT_FOUND", "Không tìm thấy menu cha với ID: " + parentId);
        }
        Map<Long, Long> parents = new HashMap<>();
        all.values().forEach(menu -> parents.put(menu.getId(), menu.getParentId()));
        parents.put(id, parentId);
        ensureNoCycle(id, parents);
    }

    private static void ensureNoCycle(Long id, Map<Long, Long> parents) {
        Set<Long> visited = new HashSet<>();
        Long current = id;
        while (current != null) {
            if (!visited.add(current)) {
                throw new OracleBusinessException("MENU_PARENT_INVALID",
                        "Menu cha không hợp lệ: không được chọn chính nó hoặc menu con của nó.");
            }
            current = parents.get(current);
        }
    }

    private int nextSortOrder(Long parentId) {
        return allMenus().values().stream()
                .filter(menu -> Objects.equals(menu.getParentId(), parentId))
                .map(MenuEntity::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(1);
    }

    private Map<Long, MenuEntity> allMenus() {
        Map<Long, MenuEntity> result = new LinkedHashMap<>();
        for (MenuEntity menu : menuRepository.findByIsDeletedOrderBySortOrderAscIdAsc(PersistenceFlags.NOT_DELETED)) {
            result.put(menu.getId(), menu);
        }
        return result;
    }

    private Map<Long, List<FunctionEntity>> functionsByMenu() {
        Map<Long, List<FunctionEntity>> result = new HashMap<>();
        for (FunctionEntity function : functionRepository.findByIsDeletedOrderByIdAsc(PersistenceFlags.NOT_DELETED)) {
            result.computeIfAbsent(function.getMenuId(), key -> new ArrayList<>()).add(function);
        }
        return result;
    }

    private MenuEntity requireMenu(Long id) {
        return menuRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException("MENU_NOT_FOUND", "Không tìm thấy menu với ID: " + id));
    }

    private static String resolveMenuType(String requested, String path) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return path == null ? DomainConstants.MENU_TYPE_DIR : DomainConstants.MENU_TYPE_MENU;
    }

    private static AdminMenuResponseDto toDto(MenuEntity menu, List<FunctionEntity> functions) {
        List<FunctionOptionDto> functionDtos = functions.stream()
                .sorted(Comparator.comparing(FunctionEntity::getFunctionCode, FunctionCodes.DISPLAY_ORDER))
                .map(function -> FunctionOptionDto.builder()
                        .id(function.getId())
                        .code(function.getFunctionCode())
                        .name(function.getFunctionName())
                        .build())
                .toList();
        return AdminMenuResponseDto.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .code(menu.getMenuCode())
                .name(menu.getMenuName())
                .menuType(menu.getMenuType())
                .path(menu.getPath())
                .icon(menu.getIcon())
                .sortOrder(menu.getSortOrder())
                .active(DomainConstants.RECORD_STATUS_ACTIVE.equals(menu.getStatus()))
                .hidden(Integer.valueOf(1).equals(menu.getIsHidden()))
                .functions(new ArrayList<>(functionDtos))
                .children(new ArrayList<>())
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
