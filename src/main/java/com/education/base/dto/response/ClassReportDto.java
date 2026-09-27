package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng trong danh sách lớp học.
 * <p>
 * Các trường khớp đúng cột do {@code PRC_SEARCH_CLASSES_PAGING} trả về:
 * {@code ID, CLASS_CODE, CLASS_NAME, SUBJECT_NAME, GRADE_LEVEL, TEACHER_ID, TEACHER_NAME, ROOM_NAME,
 * START_DATE, END_DATE, CAPACITY, ENROLLED_COUNT, TUITION_AMOUNT, STATUS, CREATED_AT, UPDATED_AT}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassReportDto {

    private Long id;
    private String classCode;
    private String className;
    private String subjectName;
    private Integer gradeLevel;
    private Long teacherId;

    /** Họ tên giảng viên, lấy bằng LEFT JOIN nên có thể {@code null} khi lớp chưa phân công. */
    private String teacherName;

    private String roomName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer capacity;

    /** Số học sinh đang theo học (chỉ đếm bản ghi ghi danh trạng thái {@code ENROLLED}). */
    private Integer enrolledCount;

    private BigDecimal tuitionAmount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
