package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Mật khẩu tạm mới sau khi đặt lại ({@code POST /api/v1/student-accounts/{userId}/reset-password}).
 * Trả về MỘT lần, không lưu dạng rõ, không ghi log.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAccountCredentialDto {

    private String username;

    @ToString.Exclude
    private String tempPassword;
}
