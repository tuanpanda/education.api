package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chương trên cổng (chương khoá mẫu trước, rồi chương riêng của lớp). Chương không còn bài mở thì bỏ.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLessonSectionDto {

    private Long id;

    private String title;

    /** COURSE / CLASS. */
    private String source;

    private List<PortalLessonListItemDto> lessons;
}
