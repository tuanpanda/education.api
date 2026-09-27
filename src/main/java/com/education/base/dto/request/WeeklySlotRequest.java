package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một slot lịch tuần: thứ 2–8, giờ bắt đầu/kết thúc {@code HH:mm}, phòng, giáo viên.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklySlotRequest {

    @NotNull(message = "Thứ trong tuần không được để trống")
    @Min(value = 2, message = "Thứ trong tuần tối thiểu là 2 (Thứ Hai)")
    @Max(value = 8, message = "Thứ trong tuần tối đa là 8 (Chủ Nhật)")
    private Integer dayOfWeek;

    @NotBlank(message = "Giờ bắt đầu không được để trống")
    @Pattern(regexp = DomainConstants.TIME_HHMM_PATTERN, message = "Giờ bắt đầu phải có dạng HH:mm")
    private String startTime;

    @NotBlank(message = "Giờ kết thúc không được để trống")
    @Pattern(regexp = DomainConstants.TIME_HHMM_PATTERN, message = "Giờ kết thúc phải có dạng HH:mm")
    private String endTime;

    @Size(max = 50, message = "Tên phòng không được vượt quá 50 ký tự")
    private String roomName;

    private Long teacherId;
}
