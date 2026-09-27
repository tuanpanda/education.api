package com.education.base.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload tạo nhanh lớp học ngay trên form thêm học sinh.
 * {@code courseName} ánh xạ cột {@code SUBJECT_NAME} của {@code EDU_CLASSES}.
 * <p>
 * Không nhận {@code classCode}: mã do {@code FN_NEXT_BIZ_CODE('CLASS', GRADE_LEVEL)}
 * sinh theo {@code SYS_CODE_RULES} — ví dụ khối 9: {@code LH920260001}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickCreateClassRequest {

    @NotBlank(message = "Tên lớp không được để trống")
    @Size(max = 150, message = "Tên lớp không được vượt quá 150 ký tự")
    private String className;

    @NotNull(message = "Khối lớp không được để trống")
    @Min(value = 1, message = "Khối lớp tối thiểu là 1")
    @Max(value = 12, message = "Khối lớp tối đa là 12")
    private Integer gradeLevel;

    @Size(max = 150, message = "Tên khóa/môn học không được vượt quá 150 ký tự")
    private String courseName;

    private Long teacherId;

    @Size(max = 50, message = "Tên phòng học không được vượt quá 50 ký tự")
    private String roomName;
}
