package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Request điểm danh cả lớp trong một ngày.
 * <p>
 * Ràng buộc {@code UQ_ATTENDANCE_PER_DAY (CLASS_ID, STUDENT_ID, ATTENDANCE_DATE)} cho phép
 * đúng một bản ghi mỗi học sinh mỗi ngày, nên tầng Service phải xử lý theo ngữ nghĩa
 * upsert (ghi mới nếu chưa có, cập nhật nếu điểm danh lại).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceMarkRequest {

    @NotNull(message = "ID lớp học không được để trống")
    private Long classId;

    @NotNull(message = "Ngày điểm danh không được để trống")
    private LocalDate attendanceDate;

    @NotEmpty(message = "Danh sách điểm danh không được để trống")
    @Valid
    private List<Entry> entries;

    /**
     * Kết quả điểm danh của một học sinh.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Entry {

        @NotNull(message = "ID học sinh không được để trống")
        private Long studentId;

        @NotBlank(message = "Trạng thái điểm danh không được để trống")
        @Pattern(regexp = DomainConstants.ATTENDANCE_STATUS_PATTERN,
                message = "Trạng thái điểm danh chỉ nhận: PRESENT, ABSENT, LATE, EXCUSED")
        private String status;

        @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
        private String note;
    }
}
