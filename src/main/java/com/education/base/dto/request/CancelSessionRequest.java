package com.education.base.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hủy buổi học {@code SCHEDULED}, bắt buộc lý do (lưu {@code NOTE}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelSessionRequest {

    @NotBlank(message = "Lý do hủy buổi học không được để trống")
    @Size(max = 500, message = "Lý do hủy không được vượt quá 500 ký tự")
    private String reason;
}
