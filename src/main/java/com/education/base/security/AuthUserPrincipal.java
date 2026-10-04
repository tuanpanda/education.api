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

    /**
     * Loại tài khoản ({@code SYS_USERS.USER_TYPE}). Mặc định {@link UserType#STAFF} cho principal dựng tay
     * (test); principal thật luôn được {@code AccessControlService.buildPrincipal} gán từ DB.
     */
    @Builder.Default
    private final UserType userType = UserType.STAFF;

    /**
     * Học sinh của tài khoản {@link UserType#STUDENT} (liên kết {@code SELF} đang hoạt động trong
     * {@code EDU_USER_STUDENT_LINKS}); {@code null} với tài khoản khác hoặc khi chưa có liên kết.
     */
    private final Long studentId;

    @Override
    public String getName() {
        return username;
    }

    /** Tài khoản nhân viên (được dùng API quản trị). */
    public boolean isStaff() {
        return userType == UserType.STAFF;
    }

    /** Người dùng có vai trò quản trị tối cao ({@link Permissions#ADMIN_ROLE}) - bỏ qua kiểm tra quyền chi tiết. */
    public boolean isAdmin() {
        // Hàng rào kép: tài khoản không phải nhân viên không bao giờ được coi là quản trị viên,
        // kể cả khi lỡ bị gán ROLE_ADMIN.
        return isStaff() && roles != null && roles.contains(Permissions.ADMIN_ROLE);
    }

    public boolean hasPermission(String permission) {
        if (!isStaff()) {
            return false;
        }
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
