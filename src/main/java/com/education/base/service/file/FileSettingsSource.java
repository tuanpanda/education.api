package com.education.base.service.file;

import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.dto.response.FileQuotaDto;

import java.util.List;
import java.util.Optional;

/**
 * Nguồn cấu hình giới hạn upload theo module ({@code SYS_FILE_MODULE_SETTINGS}) và hạn mức
 * ({@code SYS_FILE_QUOTAS}).
 * <p>
 * Luồng A định nghĩa và dùng interface này trong kiểm tra giới hạn upload; khi chưa có bean nào
 * (Luồng B chưa hợp nhất) Luồng A dùng giá trị mặc định {@code app.storage.defaults.*} /
 * {@code app.storage.quota.*}. Luồng B cài đặt bằng bảng V19_2 (có cache ngắn, xoá cache khi admin lưu).
 * <p>
 * Chỉ Luồng A sửa file này; lớp cài đặt thuộc Luồng B.
 */
public interface FileSettingsSource {

    /** Cấu hình của một module, rỗng khi module chưa có dòng cấu hình. */
    Optional<FileModuleSettingDto> moduleSetting(String moduleName);

    /** Mọi dòng cấu hình module (màn hình cấu hình, chính sách upload cho frontend). */
    List<FileModuleSettingDto> moduleSettings();

    /**
     * Hạn mức theo phạm vi ({@code FileConstants.QuotaScope}); {@code scopeId = null} với
     * {@code GLOBAL} / {@code STUDENT_DEFAULT}.
     */
    Optional<FileQuotaDto> quota(String scopeType, Long scopeId);
}
