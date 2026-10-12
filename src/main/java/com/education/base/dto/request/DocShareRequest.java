package com.education.base.dto.request;

import com.education.base.common.document.DocumentConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một đích chia sẻ trong {@link DocSharesUpdateRequest}. ALL_STAFF: {@code targetId} NULL; ROLE: {@code SYS_ROLES.ID};
 * CLASS: {@code EDU_CLASSES.ID}. {@code visibleToStudents} chỉ hợp lệ với CLASS. Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocShareRequest {

    @NotBlank
    @Pattern(regexp = DocumentConstants.TARGET_TYPE_PATTERN, message = "Đích chia sẻ chỉ nhận: ALL_STAFF, ROLE, CLASS")
    private String targetType;

    private Long targetId;

    private boolean visibleToStudents;

    /** NULL = không hết hạn; phải ở tương lai. */
    private LocalDateTime expiresAt;

    @Schema(hidden = true)
    @AssertTrue(message = "Đích chia sẻ không hợp lệ (ALL_STAFF không có targetId; ROLE/CLASS bắt buộc targetId; "
            + "chỉ CLASS được hiển thị cho học sinh)")
    public boolean isTargetConsistent() {
        if (targetType == null) {
            return true;
        }
        boolean idOk = DocumentConstants.TARGET_ALL_STAFF.equals(targetType) ? targetId == null : targetId != null;
        boolean studentsOk = !visibleToStudents || DocumentConstants.TARGET_CLASS.equals(targetType);
        return idOk && studentsOk;
    }
}
