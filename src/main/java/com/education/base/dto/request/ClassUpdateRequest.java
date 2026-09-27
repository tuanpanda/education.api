package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request cập nhật lớp học.
 * <p>
 * Không chứa {@code classCode}: mã lớp là business key có ràng buộc
 * {@code UQ_EDU_CLASSES_CODE}, được coi là bất biến sau khi tạo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassUpdateRequest {

    @NotBlank(message = "Tên lớp không được để trống")
    @Size(max = 150, message = "Tên lớp không được vượt quá 150 ký tự")
    private String className;

    /** Khối lớp (1–12). Cập nhật không đổi {@code CLASS_CODE} đã sinh. */
    @Min(value = 1, message = "Khối lớp tối thiểu là 1")
    @Max(value = 12, message = "Khối lớp tối đa là 12")
    private Integer gradeLevel;

    @Size(max = 150, message = "Tên môn học không được vượt quá 150 ký tự")
    private String subjectName;

    private Long teacherId;

    @Size(max = 50, message = "Tên phòng học không được vượt quá 50 ký tự")
    private String roomName;

    private LocalDate startDate;

    private LocalDate endDate;

    @Min(value = 0, message = "Sức chứa không được là số âm")
    private Integer capacity;

    @DecimalMin(value = "0", message = "Học phí không được là số âm")
    @Digits(integer = 13, fraction = 2, message = "Học phí tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal tuitionAmount;

    @NotBlank(message = "Trạng thái lớp không được để trống")
    @Pattern(regexp = DomainConstants.CLASS_STATUS_PATTERN,
            message = "Trạng thái lớp chỉ nhận: PLANNED, OPEN, ONGOING, CLOSED, CANCELLED")
    private String status;

    @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$",
            message = "Màu lịch học phải là mã hex #RRGGBB")
    @Size(max = 7, message = "Màu lịch học không được vượt quá 7 ký tự")
    private String calendarColor;
}
