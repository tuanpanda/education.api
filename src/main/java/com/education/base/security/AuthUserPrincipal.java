package com.education.base.security;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Người dùng đã xác thực, được gắn vào {@code SecurityContext} sau khi JWT hợp lệ.
 * <p>
 * Authority gồm mã vai trò (ví dụ {@code ROLE_ADMIN}) và mã quyền dạng {@code MENU_CODE:FUNCTION_CODE}.
 */
@Getter
@Builder(toBuilder = true)
public class AuthUserPrincipal implements Principal, Serializable {

    private final Long id;

    private final String username;

    private final String fullName;

    private final String email;

    @Singular
    private final List<String> roles;

    @Singular
    private final Set<String> permissions;

    private final boolean mustChangePassword;

    private final int tokenVersion;

    @Override
    public String getName() {
        return username;
    }

    /** Người dùng có vai trò quản trị tối cao ({@link Permissions#ADMIN_ROLE}) - bỏ qua kiểm tra quyền chi tiết. */
    public boolean isAdmin() {
        return roles != null && roles.contains(Permissions.ADMIN_ROLE);
    }

    public boolean hasPermission(String permission) {
        return isAdmin() || (permissions != null && permissions.contains(permission));
    }

    /** Danh sách authority cho Spring Security: vai trò + quyền phẳng. */
    public List<GrantedAuthority> getAuthorities() {
        Set<String> codes = new LinkedHashSet<>();
        if (roles != null) {
            codes.addAll(roles);
        }
        if (permissions != null) {
            codes.addAll(permissions);
        }
        List<GrantedAuthority> authorities = new ArrayList<>(codes.size());
        for (String code : codes) {
            authorities.add(new SimpleGrantedAuthority(code));
        }
        return Collections.unmodifiableList(authorities);
    }
}
