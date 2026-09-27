package com.education.base.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Sinh buổi học thực tế từ khung lịch tuần trong khoảng ngày (bao gồm hai đầu).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateSessionsRequest {

    @NotNull(message = "Từ ngày không được để trống")
    private LocalDate fromDate;

    @NotNull(message = "Đến ngày không được để trống")
    private LocalDate toDate;
}
