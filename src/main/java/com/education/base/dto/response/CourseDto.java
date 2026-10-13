package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Khoá học mẫu phía nhân viên. Danh sách: description = null.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDto {

    private Long id;

    private String courseCode;

    private String courseName;

    private String subjectName;

    private Integer gradeLevel;

    private String description;

    private String status;

    /** Số lớp đang gắn (EDU_CLASSES.COURSE_ID, lớp chưa xoá). */
    private Integer classCount;

    private Integer lessonCount;

    private Integer publishedLessonCount;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    /** Người gọi có MENU_COURSE:UPDATE. */
    private Boolean canManage;
}
