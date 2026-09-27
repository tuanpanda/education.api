package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.repository.MenuRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module Menu Đa Cấp & Quyền Người Dùng (Sidebar Navigation).
 */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Validated
@Tag(name = "Menu & Quyền", description = "Cây navigation sidebar theo userId")
public class MenuController {

    private final MenuRepository menuRepository;

    @Operation(summary = "Cây menu sidebar theo người dùng",
            description = "Gọi PRC_GET_USER_SIDEBAR_MENU, gom menu phẳng thành cây theo parentId.")
    @GetMapping("/user-navigation")
    public ApiResponse<UserNavigationResponseDto> getUserNavigation(
            @RequestParam("userId") @NotNull(message = "userId không được để trống") Long userId) {

        return ApiResponse.success(menuRepository.getUserNavigation(userId));
    }
}
