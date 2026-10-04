package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trạng thái tài khoản học sinh sau khi khóa / mở khóa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAccountStatusDto {

    private Long userId;

    private String username;

    /** Cùng giá trị với {@link StudentAccountDto#getAccountStatus()}. */
    private String accountStatus;
}
