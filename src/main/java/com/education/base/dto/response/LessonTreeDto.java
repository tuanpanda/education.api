package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code GET /api/v1/lms/lessons/tree?ownerType&ownerId}. Với CLASS: {@code sections} = chương riêng của lớp,
 * {@code inheritedSections} = chương của khoá mẫu đang gắn (chỉ đọc ở màn lớp; sửa ở màn Khoá học).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonTreeDto {

    private String ownerType;

    private Long ownerId;

    private String ownerCode;

    private String ownerName;

    /** CLASS: khoá mẫu đang gắn (NULL nếu không). */
    private Long courseId;

    private String courseName;

    private List<LessonSectionDto> sections;

    private List<LessonSectionDto> inheritedSections;

    /** Được sửa cây này (quyền + phạm vi giáo viên). */
    private Boolean canManage;
}
