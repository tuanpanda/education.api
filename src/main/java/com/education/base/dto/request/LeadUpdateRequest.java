package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request cập nhật lead. Không cho sửa {@code leadCode} (business key) và không cho đặt
 * trạng thái {@code CONVERTED} trực tiếp — việc chuyển đổi phải đi qua luồng riêng để
 * thỏa ràng buộc {@code CK_LEADS_CONVERTED}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadUpdateRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên không được vượt quá 150 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    @Pattern(regexp = "^$|^[0-9+()\\s-]{8,20}$", message = "Số điện thoại không đúng định dạng")
    private String phone;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @Pattern(regexp = DomainConstants.LEAD_SOURCE_PATTERN,
            message = "Nguồn chỉ nhận: WEBSITE, FACEBOOK, ZALO, REFERRAL, WALK_IN, HOTLINE, OTHER")
    private String source;

    @Size(max = 150, message = "Môn học quan tâm không được vượt quá 150 ký tự")
    private String interestedSubject;

    @NotBlank(message = "Trạng thái không được để trống")
    @Pattern(regexp = "NEW|CONTACTED|QUALIFIED|LOST",
            message = "Trạng thái chỉ nhận: NEW, CONTACTED, QUALIFIED, LOST. "
                    + "Để chuyển thành học sinh hãy dùng chức năng chuyển đổi riêng")
    private String status;

    private Long assignedToId;

    private LocalDate nextFollowUpDate;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
