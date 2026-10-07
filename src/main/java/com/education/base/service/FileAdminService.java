package com.education.base.service;

import com.education.base.dto.request.FileBulkRequest;
import com.education.base.dto.request.FileFilterRequest;
import com.education.base.dto.request.FileModuleSettingsUpdateRequest;
import com.education.base.dto.request.FilePurgeRequest;
import com.education.base.dto.request.FileQuotasUpdateRequest;
import com.education.base.dto.response.FileAdminDto;
import com.education.base.dto.response.FileBulkResultDto;
import com.education.base.dto.response.FileDetailDto;
import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.dto.response.FileQuotaDto;
import com.education.base.dto.response.FileStatsDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StorageUsageDto;
import com.education.base.entity.FileEntity;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Nghiệp vụ màn "Quản lý file" {@code /api/v1/admin/files/**} (Luồng B). Kiểm tra quyền thô ở controller
 * ({@code @RequirePermission MENU_FILE_MANAGER:*}); thay đổi trạng thái file đi qua
 * {@link com.education.base.service.file.FileLifecycleService} (Luồng A).
 */
public interface FileAdminService {

    PageResponse<FileAdminDto> search(FileFilterRequest filter);

    FileDetailDto detail(Long id);

    /** Nội dung để xem / tải (mọi trạng thái trừ PURGED). Ghi nhật ký tải. */
    FileContent open(Long id, boolean download);

    FileAdminDto delete(Long id, String reason);

    FileBulkResultDto bulkDelete(FileBulkRequest request);

    FileAdminDto restore(Long id);

    FileBulkResultDto bulkRestore(FileBulkRequest request);

    FileAdminDto purge(Long id, FilePurgeRequest request);

    FileStatsDto stats();

    StorageUsageDto storage();

    List<FileModuleSettingDto> moduleSettings();

    List<FileModuleSettingDto> updateModuleSettings(FileModuleSettingsUpdateRequest request);

    List<FileQuotaDto> quotas();

    List<FileQuotaDto> updateQuotas(FileQuotasUpdateRequest request);

    /**
     * @param contentType MIME an toàn để trả về ({@code application/octet-stream} nếu không nhận diện được).
     * @param inline      {@code true} chỉ khi xem và loại file an toàn để hiển thị inline (ảnh / PDF).
     */
    record FileContent(FileEntity file, Resource resource, String contentType, boolean inline) {
    }
}
