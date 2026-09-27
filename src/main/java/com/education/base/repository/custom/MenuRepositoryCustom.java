package com.education.base.repository.custom;

import com.education.base.dto.response.UserNavigationResponseDto;

/**
 * Truy vấn menu và quyền của người dùng, xử lý qua Standalone Procedure (Spring JDBC).
 */
public interface MenuRepositoryCustom {

    /**
     * Lấy cây menu sidebar và tập quyền của một người dùng, gọi Standalone Procedure
     * {@code PRC_GET_USER_SIDEBAR_MENU} (đọc 2 REF CURSOR: menu và quyền).
     *
     * @param userId ID người dùng.
     * @return cây menu đa cấp kèm tập quyền dạng {@code MENU_CODE:ACTION}.
     */
    UserNavigationResponseDto getUserNavigation(Long userId);
}
