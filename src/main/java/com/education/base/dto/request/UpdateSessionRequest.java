package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Cập nhật buổi học còn {@code SCHEDULED}: đổi ngày, giờ, phòng, giáo viên, chủ đề.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSessionRequest {

    private LocalDate sessionDate;

    @Pattern(regexp = DomainConstants.TIME_HHMM_PATTERN, message = "Giờ bắt đầu phải có dạng HH:mm")
    private String startTime;

    @Pattern(regexp = DomainConstants.TIME_HHMM_PATTERN, message = "Giờ kết thúc phải có dạng HH:mm")
    private String endTime;

    @Size(max = 50, message = "Tên phòng không được vượt quá 50 ký tự")
    private String roomName;

    private Long teacherId;

    @Size(max = 200, message = "Chủ đề không được vượt quá 200 ký tự")
    private String topic;
}
