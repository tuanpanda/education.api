package com.education.base.dto.request;

import com.education.base.common.document.DocumentConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Thay TOÀN BỘ danh sách chia sẻ trực tiếp của một thư mục / mục ({@code PUT .../folders/{id}/shares},
 * {@code PUT .../items/{id}/shares}). Danh sách rỗng = bỏ mọi chia sẻ trực tiếp (chia sẻ kế thừa không đổi).
 * Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocSharesUpdateRequest {

    @NotNull
    @Size(max = DocumentConstants.MAX_SHARES_PER_TARGET, message = "Tối đa 50 đích chia sẻ")
    @Builder.Default
    private List<@Valid @NotNull DocShareRequest> shares = List.of();
}
