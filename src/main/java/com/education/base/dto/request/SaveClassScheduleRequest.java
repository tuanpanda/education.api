package com.education.base.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Thay thế toàn bộ khung lịch tuần của một lớp. Danh sách rỗng = xóa mềm mọi slot đang ACTIVE.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveClassScheduleRequest {

    @NotNull(message = "Danh sách khung lịch không được null")
    @Size(max = 21, message = "Tối đa 21 khung lịch tuần cho một lớp")
    @Valid
    @Builder.Default
    private List<WeeklySlotRequest> slots = new ArrayList<>();
}
