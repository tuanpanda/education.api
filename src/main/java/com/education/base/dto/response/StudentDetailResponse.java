package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thông tin chi tiết một học sinh, kèm danh sách tài liệu đính kèm
 * (hồ sơ, bảng điểm...) lấy từ Module Quản lý File.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDetailResponse {

    private Long id;

    private String studentCode;

    private String fullName;

    private String email;

    private LocalDate dateOfBirth;

    private String parentName;

    private String phone;

    private String address;

    private String note;

    private String status;

    /** Lớp vừa gán khi thêm mới / lớp ghi danh gần nhất khi xem chi tiết. */
    private Long classId;

    private String classCode;

    private String className;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    /**
     * Số buổi học sinh đã có mặt ({@code PRESENT} hoặc {@code LATE}).
     */
    private Long attendedSessionCount;

    /**
     * Tổng số buổi đã được điểm danh (mọi trạng thái).
     */
    private Long attendanceMarkedCount;

    /**
     * Chi tiết từng buổi điểm danh, mới nhất trước, kèm lớp học.
     */
    @Builder.Default
    private List<StudentAttendanceSessionDto> attendanceSessions = new ArrayList<>();

    /**
     * Tài liệu đính kèm của học sinh (module {@code STUDENT}), rỗng nếu chưa có file nào.
     */
    @Builder.Default
    private List<FileResponseDto> attachments = new ArrayList<>();
}
