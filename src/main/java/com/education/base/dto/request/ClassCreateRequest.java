package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request thêm mới lớp học. Độ dài và miền giá trị khớp ràng buộc bảng {@code EDU_CLASSES}.
 * <p>
 * Không nhận {@code classCode}: mã do Function {@code FN_NEXT_BIZ_CODE}
 * sinh theo quy luật {@code SYS_CODE_RULES} (RULE_CODE = CLASS), mặc định
 * {@code {PREFIX}{GRADE}{YYYY}{SEQ}} — ví dụ khối 9: {@code LH920260001}.
 * Trigger {@code TRG_EDU_CLASSES_BI_ID} chỉ cấp khóa chính {@code ID}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassCreateRequest {

    @NotBlank(message = "Tên lớp không được để trống")
    @Size(max = 150, message = "Tên lớp không được vượt quá 150 ký tự")
    private String className;

    /**
     * Khối lớp (1–12). Được chèn vào {@code CLASS_CODE} ngay sau tiền tố chữ
     * (ví dụ khối 9, PREFIX=LH → {@code LH9...}).
     */
    @NotNull(message = "Khối lớp không được để trống")
    @Min(value = 1, message = "Khối lớp tối thiểu là 1")
    @Max(value = 12, message = "Khối lớp tối đa là 12")
    private Integer gradeLevel;

    @Size(max = 150, message = "Tên môn học không được vượt quá 150 ký tự")
    private String subjectName;

    /** ID giảng viên trong {@code SYS_USERS}; có thể để trống khi lớp chưa phân công. */
    private Long teacherId;

    @Size(max = 50, message = "Tên phòng học không được vượt quá 50 ký tự")
    private String roomName;

    private LocalDate startDate;

    /**
     * Ngày kết thúc phải không nhỏ hơn {@code startDate} (ràng buộc {@code CK_CLASSES_PERIOD}).
     */
    private LocalDate endDate;

    @Min(value = 0, message = "Sức chứa không được là số âm")
    private Integer capacity;

    @DecimalMin(value = "0", message = "Học phí không được là số âm")
    @Digits(integer = 13, fraction = 2, message = "Học phí tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal tuitionAmount;

    @Pattern(regexp = DomainConstants.CLASS_STATUS_PATTERN,
            message = "Trạng thái lớp chỉ nhận: PLANNED, OPEN, ONGOING, CLOSED, CANCELLED")
    private String status;

    /** Màu trên lịch học, dạng {@code #RRGGBB}. Để trống thì hệ thống gán màu mặc định. */
    @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$",
            message = "Màu lịch học phải là mã hex #RRGGBB")
    @Size(max = 7, message = "Màu lịch học không được vượt quá 7 ký tự")
    private String calendarColor;
}
