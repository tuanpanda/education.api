package com.education.base.dto.request;

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
 * Request cập nhật thông tin học sinh.
 * <p>
 * Không chứa {@code studentCode}: mã học sinh là business key có ràng buộc
 * {@code UQ_EDU_STUDENTS_CODE} và đã được dùng để tham chiếu trên hồ sơ/biên lai,
 * nên được coi là bất biến sau khi tạo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentUpdateRequest {

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

    @NotBlank(message = "Trạng thái không được để trống")
    @Pattern(regexp = "ACTIVE|INACTIVE|GRADUATED|SUSPENDED",
            message = "Trạng thái chỉ nhận một trong các giá trị: ACTIVE, INACTIVE, GRADUATED, SUSPENDED")
    private String status;

    /** Ghi danh vào lớp đang mở (bỏ trống nếu không đổi lớp). */
    private Long classId;

    public StudentUpdateRequest(String fullName, String email, String status) {
        this.fullName = fullName;
        this.email = email;
        this.status = status;
    }
}
