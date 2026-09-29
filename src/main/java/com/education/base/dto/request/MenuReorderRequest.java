package com.education.base.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sắp xếp lại menu: cập nhật {@code parentId} và {@code sortOrder} cho nhiều menu trong một transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuReorderRequest {

    @NotEmpty(message = "Danh sách menu cần sắp xếp không được để trống")
    @Builder.Default
    private List<@Valid @NotNull Item> items = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {

        @NotNull(message = "id không được để trống")
        private Long id;

        /** Menu cha mới; {@code null} là menu gốc. */
        private Long parentId;

        @NotNull(message = "sortOrder không được để trống")
        @Min(value = 0, message = "Thứ tự phải lớn hơn hoặc bằng 0")
        @Max(value = 99999, message = "Thứ tự không được vượt quá 99999")
        private Integer sortOrder;
    }
}
