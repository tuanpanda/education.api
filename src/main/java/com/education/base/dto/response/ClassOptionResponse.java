package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dòng rút gọn cho dropdown chọn lớp trên form thêm học sinh.
 * {@code courseName} lấy từ cột {@code SUBJECT_NAME}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassOptionResponse {

    private Long id;

    private String classCode;

    private String className;

    private Integer gradeLevel;

    private String courseName;

    private String status;

    private String calendarColor;
}
