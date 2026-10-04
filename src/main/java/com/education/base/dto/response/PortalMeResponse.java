package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Hồ sơ học sinh trên cổng ({@code GET /api/v1/portal/me}). DTO riêng cho cổng: không có {@code NOTE} nội bộ,
 * địa chỉ, {@code CREATED_BY}... của {@code EDU_STUDENTS}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalMeResponse {

    private Long studentId;

    private String studentCode;

    private String fullName;

    private LocalDate dateOfBirth;

    /**
     * Giới tính. {@code EDU_STUDENTS} hiện CHƯA có cột giới tính nên luôn {@code null}; giữ trường để hợp đồng
     * API ổn định khi bổ sung cột.
     */
    private String gender;

    /** Lớp đang ghi danh ({@code ENROLLED}), lớp ghi danh gần nhất trước. */
    @Builder.Default
    private List<PortalClassDto> classes = new ArrayList<>();

    private String parentName;

    private String phone;

    private String email;
}
