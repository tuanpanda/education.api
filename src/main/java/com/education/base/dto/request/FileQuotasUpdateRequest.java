package com.education.base.dto.request;

import com.education.base.dto.response.FileQuotaDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code PUT /api/v1/admin/files/settings/quotas}: upsert theo (scopeType, scopeId). Xoá quota STUDENT/USER: liệt kê
 * ID trong {@code deleteIds} (không xoá được GLOBAL / STUDENT_DEFAULT). Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileQuotasUpdateRequest {

    @Size(max = 200)
    @Builder.Default
    private List<@Valid @NotNull FileQuotaDto> quotas = List.of();

    @Size(max = 200)
    @Builder.Default
    private List<@NotNull Long> deleteIds = List.of();
}
