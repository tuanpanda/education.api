package com.education.base.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request chuyển một lead thành học sinh chính thức.
 * <p>
 * Luồng nghiệp vụ phải thực hiện trong CÙNG một transaction: tạo bản ghi
 * {@code EDU_STUDENTS}, gán {@code CONVERTED_STUDENT_ID} và đặt trạng thái lead thành
 * {@code CONVERTED}. Ràng buộc {@code CK_LEADS_CONVERTED} bắt buộc hai bước này đi cùng nhau,
 * nên không được tách thành hai transaction riêng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadConvertRequest {

    /**
     * Không còn dùng. Mã học sinh do {@code FN_NEXT_BIZ_CODE} sinh theo
     * {@code SYS_CODE_RULES} khi INSERT. Client gửi lên sẽ bị bỏ qua.
     */
    @Size(max = 30, message = "Mã học sinh không được vượt quá 30 ký tự")
    @Pattern(regexp = "^$|^[A-Za-z0-9_-]+$",
            message = "Mã học sinh chỉ được chứa chữ, số, dấu gạch ngang và gạch dưới")
    private String studentCode;

    /**
     * Họ tên chính thức trên hồ sơ. Bỏ trống thì lấy theo họ tên đã lưu ở lead.
     */
    @Size(max = 150, message = "Họ tên không được vượt quá 150 ký tự")
    private String fullName;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    /**
     * Lớp học ghi danh ngay sau khi chuyển đổi; bỏ trống thì chưa ghi danh.
     */
    private Long enrollClassId;

    @NotBlank(message = "Ghi chú chuyển đổi không được để trống")
    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
