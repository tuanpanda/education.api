package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Điều kiện tra cứu lịch sử điểm danh. Bỏ trống trường nào là không lọc theo trường đó.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceFilterRequest {

    private Long classId;

    private Long studentId;

    private LocalDate fromDate;

    private LocalDate toDate;

    @Pattern(regexp = DomainConstants.ATTENDANCE_STATUS_PATTERN,
            message = "Trạng thái điểm danh chỉ nhận: PRESENT, ABSENT, LATE, EXCUSED")
    private String status;
}
