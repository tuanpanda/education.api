package com.education.base.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request nhập điểm hàng loạt. Toàn bộ danh sách được ghi trong một transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeBatchRequest {

    @NotEmpty(message = "Danh sách điểm không được để trống")
    @Valid
    private List<GradeUpsertRequest> grades;
}
