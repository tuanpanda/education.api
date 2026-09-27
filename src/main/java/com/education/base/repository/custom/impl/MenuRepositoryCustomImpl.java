package com.education.base.repository.custom.impl;

import com.education.base.dto.response.MenuItemResponseDto;
import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.repository.base.OracleProcExecutor;
import com.education.base.repository.custom.MenuRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Triển khai {@link MenuRepositoryCustom} bằng Standalone Procedure
 * {@code PRC_GET_USER_SIDEBAR_MENU(P_USER_ID, O_MENU_CURSOR, O_PERMISSIONS_CURSOR, O_ERR_CODE, O_ERR_MSG)}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MenuRepositoryCustomImpl implements MenuRepositoryCustom {

    private static final String PROC_NAME = "PRC_GET_USER_SIDEBAR_MENU";

    private static final Comparator<MenuItemResponseDto> MENU_ORDER =
            Comparator.comparing(MenuItemResponseDto::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(MenuItemResponseDto::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final RowMapper<MenuItemResponseDto> MENU_ROW_MAPPER = (rs, rowNum) -> {
        Object parentId = rs.getObject("PARENT_ID");
        Object sortOrder = rs.getObject("SORT_ORDER");
        return MenuItemResponseDto.builder()
                .id(rs.getLong("ID"))
                .parentId(parentId == null ? null : rs.getLong("PARENT_ID"))
                .menuCode(rs.getString("MENU_CODE"))
                .menuName(rs.getString("MENU_NAME"))
                .menuType(rs.getString("MENU_TYPE"))
                .path(rs.getString("PATH"))
                .icon(rs.getString("ICON"))
                .sortOrder(sortOrder == null ? null : rs.getInt("SORT_ORDER"))
                .allowedFunctions(splitAllowedFunctions(rs.getString("ALLOWED_FUNCTIONS")))
                .children(new ArrayList<>())
                .build();
    };

    private static final RowMapper<String> PERMISSION_ROW_MAPPER = (rs, rowNum) -> rs.getString("PERMISSION_KEY");

    private final OracleProcExecutor oracleProcExecutor;

    @Override
    public UserNavigationResponseDto getUserNavigation(Long userId) {
        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_NAME)
                .declareParameters(
                        new SqlParameter("P_USER_ID", Types.NUMERIC),
                        new SqlOutParameter("O_MENU_CURSOR", OracleTypes.CURSOR, MENU_ROW_MAPPER),
                        new SqlOutParameter("O_PERMISSIONS_CURSOR", OracleTypes.CURSOR, PERMISSION_ROW_MAPPER),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> out = call.execute(Map.of("P_USER_ID", userId));
        oracleProcExecutor.validateResult(out);

        List<MenuItemResponseDto> flatMenus =
                ProcCursorReader.readCursor(out, "O_MENU_CURSOR", MenuItemResponseDto.class);
        Set<String> permissions =
                new LinkedHashSet<>(ProcCursorReader.readCursor(out, "O_PERMISSIONS_CURSOR", String.class));

        List<MenuItemResponseDto> tree = buildTree(flatMenus);
        log.debug("{} trả về {} menu ({} menu gốc) và {} quyền cho userId={}",
                PROC_NAME, flatMenus.size(), tree.size(), permissions.size(), userId);

        return UserNavigationResponseDto.builder()
                .menus(tree)
                .permissions(permissions)
                .build();
    }

    /**
     * Gom danh sách menu phẳng thành cây phân cấp đa tầng dựa trên {@code parentId}.
     * <p>
     * Procedure không đảm bảo menu cha xuất hiện trước menu con, nên toàn bộ menu được
     * lập chỉ mục trước khi nối quan hệ. Menu có {@code parentId} không nằm trong danh sách
     * được phép xem sẽ được đưa lên mức gốc để không bị mất khỏi sidebar.
     *
     * @param flatMenus danh sách menu phẳng lấy từ cursor.
     * @return danh sách menu gốc, mỗi menu đã được nối menu con và sắp xếp theo {@code sortOrder}.
     */
    static List<MenuItemResponseDto> buildTree(List<MenuItemResponseDto> flatMenus) {
        Map<Long, MenuItemResponseDto> menuById = new LinkedHashMap<>();
        for (MenuItemResponseDto menu : flatMenus) {
            if (menu.getChildren() == null) {
                menu.setChildren(new ArrayList<>());
            }
            menuById.put(menu.getId(), menu);
        }

        List<MenuItemResponseDto> roots = new ArrayList<>();
        for (MenuItemResponseDto menu : menuById.values()) {
            MenuItemResponseDto parent = menu.getParentId() == null ? null : menuById.get(menu.getParentId());
            if (parent == null || parent == menu) {
                roots.add(menu);
            } else {
                parent.getChildren().add(menu);
            }
        }

        sortRecursively(roots);
        return roots;
    }

    private static void sortRecursively(List<MenuItemResponseDto> menus) {
        menus.sort(MENU_ORDER);
        for (MenuItemResponseDto menu : menus) {
            sortRecursively(menu.getChildren());
        }
    }

    /**
     * Tách chuỗi {@code ALLOWED_FUNCTIONS} dạng {@code VIEW,CREATE,UPDATE} thành danh sách mã chức năng.
     */
    private static List<String> splitAllowedFunctions(String allowedFunctions) {
        if (allowedFunctions == null || allowedFunctions.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(allowedFunctions.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
