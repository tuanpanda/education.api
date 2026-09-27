package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chi tiết một lớp học, kèm danh sách học sinh đã ghi danh và tài liệu đính kèm.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassDetailResponse {

    private Long id;
    private String classCode;
    private String className;

    /** Khối lớp (1–12) đã gắn khi tạo, dùng trong mã lớp. */
    private Integer gradeLevel;

    private String subjectName;
    private Long teacherId;
    private String teacherName;
    private String roomName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer capacity;
    private BigDecimal tuitionAmount;
    private String status;
    private String calendarColor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    /** Danh sách học sinh đã ghi danh vào lớp. */
    @Builder.Default
    private List<EnrolledStudentDto> students = new ArrayList<>();

    /** Tài liệu của lớp (giáo trình, lịch học...), module {@code CLASS}. */
    @Builder.Default
    private List<FileResponseDto> attachments = new ArrayList<>();
}
