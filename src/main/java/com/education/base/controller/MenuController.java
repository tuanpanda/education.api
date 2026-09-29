package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.security.AllowPendingPasswordChange;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AccessControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module Menu Đa Cấp & Quyền Người Dùng (Sidebar Navigation).
 */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Tag(name = "Menu & Quyền", description = "Cây navigation sidebar của người dùng đang đăng nhập")
public class MenuController {

    private final AccessControlService accessControlService;

    @Operation(summary = "Cây menu sidebar của người dùng đang đăng nhập",
            description = "Người dùng lấy từ access token; chỉ trả menu có quyền VIEW (kèm menu cha) và danh sách quyền phẳng.")
    @GetMapping("/user-navigation")
    @AllowPendingPasswordChange
    public ApiResponse<UserNavigationResponseDto> getUserNavigation() {
        return ApiResponse.success(accessControlService.getNavigation(SecurityUtils.requireCurrentUser()));
    }
}
