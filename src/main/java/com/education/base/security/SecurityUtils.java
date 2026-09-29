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
