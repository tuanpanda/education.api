package com.education.base.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Body (không bắt buộc) của {@code POST /api/v1/auth/logout}. Gửi kèm refresh token để thu hồi đúng phiên
 * của nó; bỏ trống thì dùng phiên (claim {@code sid}) của access token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {

    @Size(max = 4096, message = "Refresh token không hợp lệ")
    @ToString.Exclude
    private String refreshToken;
}
