package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/lms/classes/{classId}/lesson-progress}: bài PUBLISHED lớp nhìn thấy (khoá mẫu + riêng lớp) x
 * học sinh ENROLLED. {@code statuses}: lessonId -> NOT_STARTED / IN_PROGRESS / COMPLETED (JSON key là chuỗi).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassLessonProgressDto {

    private Long classId;

    private String classCode;

    private String className;

    private List<LessonColumn> lessons;

    private List<StudentRow> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LessonColumn {
        private Long lessonId;
        private String title;
        private String sectionTitle;
        /** COURSE / CLASS. */
        private String source;
        private Boolean availableNow;
        private Integer completedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentRow {
        private Long studentId;
        private String studentCode;
        private String fullName;
        private Integer completedCount;
        /** Mẫu số = số bài availableNow. */
        private Integer totalLessons;
        /** 0..100, làm tròn số nguyên. */
        private Integer percent;
        private LocalDateTime lastViewedAt;
        private Map<Long, String> statuses;
    }
}
