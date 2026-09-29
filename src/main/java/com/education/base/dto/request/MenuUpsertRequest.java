package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tạo mới / cập nhật menu. Khi cập nhật, {@code code} phải giữ nguyên
 * (mã menu được tham chiếu bởi mã quyền {@code MENU_CODE:FUNCTION_CODE}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuUpsertRequest {

    /** Menu cha; {@code null} là menu gốc. */
    private Long parentId;

    @NotBlank(message = "Mã menu không được để trống")
    @Pattern(regexp = DomainConstants.SYSTEM_CODE_PATTERN,
            message = "Mã menu chỉ gồm chữ IN HOA, số, gạch dưới (2-50 ký tự), ví dụ MENU_STUDENT_LIST")
    private String code;

    @NotBlank(message = "Tên menu không được để trống")
    @Size(max = 100, message = "Tên menu không được vượt quá 100 ký tự")
    private String name;

    /** {@code DIR} hoặc {@code MENU}; bỏ trống thì tự suy ra (có path là MENU). */
    @Pattern(regexp = DomainConstants.MENU_TYPE_PATTERN, message = "Loại menu chỉ nhận: DIR, MENU")
    private String menuType;

    @Size(max = 255, message = "Đường dẫn không được vượt quá 255 ký tự")
    @Pattern(regexp = "^$|^/[A-Za-z0-9/_\\-:.]*$", message = "Đường dẫn phải bắt đầu bằng '/'")
    private String path;

    @Size(max = 50, message = "Icon không được vượt quá 50 ký tự")
    private String icon;

    @Min(value = 0, message = "Thứ tự phải lớn hơn hoặc bằng 0")
    @Max(value = 99999, message = "Thứ tự không được vượt quá 99999")
    private Integer sortOrder;

    /** Mặc định {@code true}. */
    private Boolean active;

    /** Ẩn khỏi sidebar nhưng vẫn phân quyền được. Mặc định {@code false}. */
    private Boolean hidden;

    /**
     * Mã chức năng của menu (VIEW luôn được thêm). Bỏ trống khi tạo mới thì dùng mặc định
     * theo loại menu; bỏ trống khi cập nhật thì giữ nguyên.
     */
    private List<@NotNull @Pattern(regexp = DomainConstants.SYSTEM_CODE_PATTERN,
            message = "Mã chức năng không hợp lệ") String> functionCodes;
}
