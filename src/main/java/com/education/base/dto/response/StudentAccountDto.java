package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng màn hình "Tài khoản học sinh": học sinh kèm tài khoản {@code SELF} (nếu có).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAccountDto {

    /** Chưa có tài khoản. */
    public static final String NO_ACCOUNT = "NO_ACCOUNT";
    /** Có tài khoản nhưng chưa đăng nhập lần nào. */
    public static final String NEVER_LOGGED_IN = "NEVER_LOGGED_IN";
    /** Đang hoạt động (đã đăng nhập ít nhất một lần). */
    public static final String ACTIVE = "ACTIVE";
    /** Bị khóa ({@code SYS_USERS.STATUS = LOCKED}). */
    public static final String LOCKED = "LOCKED";
    /** Ngừng hoạt động ({@code SYS_USERS.STATUS = INACTIVE}). */
    public static final String INACTIVE = "INACTIVE";

    private Long studentId;

    private String studentCode;

    private String fullName;

    private LocalDate dateOfBirth;

    /** {@code EDU_STUDENTS.STATUS}. */
    private String studentStatus;

    /** {@code SYS_USERS.ID} của tài khoản (dùng cho reset-password / lock / unlock); {@code null} nếu chưa có. */
    private Long userId;

    private String username;

    /** {@value #NO_ACCOUNT} / {@value #NEVER_LOGGED_IN} / {@value #ACTIVE} / {@value #LOCKED} / {@value #INACTIVE}. */
    private String accountStatus;

    /** Tài khoản còn phải đổi mật khẩu tạm. */
    private boolean mustChangePassword;

    private LocalDateTime lastLoginAt;
}
