package com.education.base.security;

import com.education.base.exception.UnauthorizedException;
import lombok.experimental.UtilityClass;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Truy xuất người dùng hiện tại từ {@code SecurityContext}.
 */
@UtilityClass
public class SecurityUtils {

    public Optional<AuthUserPrincipal> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /**
     * Người dùng hiện tại; ném {@link UnauthorizedException} nếu chưa đăng nhập bằng JWT hợp lệ.
     */
    public AuthUserPrincipal requireCurrentUser() {
        return currentUser().orElseThrow(() -> new UnauthorizedException(
                "UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục."));
    }

    /** Người dùng hiện tại có vai trò quản trị tối cao {@link Permissions#ADMIN_ROLE}. */
    public boolean isCurrentUserAdmin() {
        return currentUser().map(AuthUserPrincipal::isAdmin).orElse(false);
    }

    /**
     * Người dùng hiện tại có mã quyền {@code permission} ({@code MENU_CODE:FUNCTION_CODE}); quản trị viên luôn có.
     * Chưa đăng nhập -> {@code false}.
     */
    public boolean currentUserHasPermission(String permission) {
        return currentUser().map(principal -> principal.hasPermission(permission)).orElse(false);
    }

    /** Tên đăng nhập của người thao tác (ghi vào CREATED_BY/UPDATED_BY), mặc định {@code SYSTEM}. */
    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "SYSTEM";
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "SYSTEM" : name;
    }
}
