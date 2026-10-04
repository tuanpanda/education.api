package com.education.base.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Tạo hàng loạt tài khoản học sinh ({@code POST /api/v1/student-accounts/bulk}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAccountBulkRequest {

    public static final int MAX_STUDENTS = 200;

    @NotEmpty(message = "Vui lòng chọn ít nhất một học sinh")
    @Size(max = MAX_STUDENTS, message = "Mỗi lần chỉ tạo tối đa 200 tài khoản")
    @Builder.Default
    private List<@NotNull(message = "ID học sinh không được để trống") Long> studentIds = new ArrayList<>();
}
