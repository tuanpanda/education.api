package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin rút gọn của vai trò (dùng khi hiển thị vai trò của người dùng).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleSummaryDto {

    private Long id;

    private String roleCode;

    private String roleName;
}
