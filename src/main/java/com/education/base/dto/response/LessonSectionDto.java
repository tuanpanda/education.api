package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chương trong cây phía nhân viên.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonSectionDto {

    private Long id;

    private String ownerType;

    private Long ownerId;

    private String title;

    private Integer sortOrder;

    private Long version;

    private List<LessonSummaryDto> lessons;
}
