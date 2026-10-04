package com.education.base.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Loại tài khoản ({@code SYS_USERS.USER_TYPE}, V17_1).
 * <ul>
 *     <li>{@link #STAFF}: nhân viên / quản trị - dùng ứng dụng quản trị, phân quyền theo menu x chức năng.</li>
 *     <li>{@link #STUDENT}: học sinh - CHỈ được gọi {@code /api/v1/auth/**} và {@code /api/v1/portal/**}.</li>
 *     <li>{@link #PARENT}: phụ huynh (giai đoạn sau) - hiện chỉ được gọi {@code /api/v1/auth/**}.</li>
 * </ul>
 * Hàng rào theo loại tài khoản nằm ở {@link PermissionInterceptor}.
 */
public enum UserType {
    STAFF, STUDENT, PARENT;

    /**
     * Giá trị trong DB -> enum. {@code null} (dòng tạo trước V17_1, cột có DEFAULT 'STAFF') coi là {@link #STAFF};
     * giá trị lạ -> rỗng (người gọi phải từ chối, không đoán).
     */
    public static Optional<UserType> fromDb(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(STAFF);
        }
        try {
            return Optional.of(UserType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
