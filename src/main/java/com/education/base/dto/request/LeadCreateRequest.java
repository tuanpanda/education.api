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
 * Request thêm mới một nguồn tuyển sinh (lead).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadCreateRequest {

    @NotBlank(message = "Mã lead không được để trống")
    @Size(max = 30, message = "Mã lead không được vượt quá 30 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "Mã lead chỉ được chứa chữ, số, dấu gạch ngang và gạch dưới")
    private String leadCode;

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

    /** ID chuyên viên phụ trách trong {@code SYS_USERS}. */
    private Long assignedToId;

    private LocalDate nextFollowUpDate;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
