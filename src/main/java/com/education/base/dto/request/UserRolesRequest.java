package com.education.base.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Gán lại toàn bộ vai trò cho người dùng (thay thế danh sách hiện có).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesRequest {

    @NotNull(message = "Danh sách vai trò không được để trống")
    private List<@NotNull(message = "roleId không được để trống") Long> roleIds = new ArrayList<>();
}
