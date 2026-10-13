package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/v1/portal/me/lessons}: mỗi lớp ENROLLED một dòng, kèm % hoàn thành. Không lộ thông tin nội bộ.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLessonClassDto {

    private Long classId;

    private String classCode;

    private String className;

    private String subjectName;

    private String courseName;

    /** Bài đang mở (PUBLISHED, đã tới giờ mở). */
    private Integer totalLessons;

    private Integer completedLessons;

    private Integer percent;

    /** Bài nên học tiếp: bài mở đầu tiên chưa COMPLETED (NULL nếu xong hết). */
    private Long continueLessonId;

    private String continueLessonTitle;
}
