package com.education.base.dto.request;

import com.education.base.dto.response.FileModuleSettingDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code PUT /api/v1/admin/files/settings/modules}: cập nhật (upsert theo moduleName) danh sách cấu hình module.
 * Module không có trong danh sách giữ nguyên. Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileModuleSettingsUpdateRequest {

    @NotEmpty
    @Size(max = 50)
    private List<@Valid @NotNull FileModuleSettingDto> settings;
}
