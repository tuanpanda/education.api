package com.education.base.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request thêm mới học sinh. Độ dài các trường khớp đúng ràng buộc cột của bảng
 * {@code EDU_STUDENTS} để lỗi được chặn ngay tại tầng Controller thay vì để Oracle
 * ném {@code ORA-12899 (value too large for column)}.
 * <p>
 * Không nhận {@code studentCode}: mã do Function {@code FN_NEXT_BIZ_CODE}
 * sinh theo quy luật {@code SYS_CODE_RULES} (RULE_CODE = STUDENT).
 * Trigger {@code TRG_EDU_STUDENTS_BI_ID} chỉ cấp khóa chính {@code ID}.
 * <p>
 * Gán lớp (tùy chọn, không gửi đồng thời cả hai):
 * <ul>
 *   <li>{@code classId} — ghi danh vào lớp có sẵn</li>
 *   <li>{@code newClass} — tạo nhanh lớp rồi ghi danh trong cùng giao dịch</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentCreateRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên không được vượt quá 150 ký tự")
    private String fullName;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @PastOrPresent(message = "Ngày sinh không được ở tương lai")
    private LocalDate dateOfBirth;

    @Size(max = 100, message = "Phụ huynh không được vượt quá 100 ký tự")
    private String parentName;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    @Pattern(regexp = "^$|^[0-9+()\\s.-]{8,20}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;

    /**
     * Trạng thái học sinh. Để trống sẽ mặc định là {@code ACTIVE}.
     */
    @Pattern(regexp = "ACTIVE|INACTIVE|GRADUATED|SUSPENDED",
            message = "Trạng thái chỉ nhận một trong các giá trị: ACTIVE, INACTIVE, GRADUATED, SUSPENDED")
    private String status;

    /** ID lớp có sẵn. Null nếu không ghi danh hoặc đang tạo nhanh lớp mới. */
    private Long classId;

    /** Tạo nhanh lớp mới rồi ghi danh. Null nếu không tạo lớp. */
    @Valid
    private QuickCreateClassRequest newClass;

    public StudentCreateRequest(String fullName, String email, String status) {
        this.fullName = fullName;
        this.email = email;
        this.status = status;
    }
}
