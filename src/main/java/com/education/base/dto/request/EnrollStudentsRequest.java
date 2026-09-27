package com.education.base.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request ghi danh một hoặc nhiều học sinh vào lớp.
 * <p>
 * Ràng buộc {@code UQ_CLASS_STUDENT (CLASS_ID, STUDENT_ID)} đảm bảo một học sinh không thể
 * ghi danh trùng vào cùng một lớp, nên tầng Service phải bỏ qua hoặc báo lỗi rõ ràng với
 * các ID đã tồn tại thay vì để Oracle ném {@code ORA-00001}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollStudentsRequest {

    @NotNull(message = "ID lớp học không được để trống")
    private Long classId;

    @NotEmpty(message = "Danh sách học sinh không được để trống")
    private List<@NotNull(message = "ID học sinh không được để trống") Long> studentIds;
}
