package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Kết quả tạo tài khoản cho một học sinh ({@code POST /api/v1/student-accounts/bulk}).
 * {@code tempPassword} chỉ có khi {@code status = CREATED}, trả về MỘT lần và không bao giờ ghi log
 * ({@link ToString.Exclude}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAccountBulkResultDto {

    public static final String CREATED = "CREATED";
    public static final String SKIPPED = "SKIPPED";

    /** Không tìm thấy học sinh (hoặc đã xóa). */
    public static final String REASON_STUDENT_NOT_FOUND = "STUDENT_NOT_FOUND";
    /** Học sinh không ở trạng thái ACTIVE. */
    public static final String REASON_STUDENT_INACTIVE = "STUDENT_INACTIVE";
    /** Học sinh đã có tài khoản SELF đang hoạt động ({@code username} = tài khoản hiện có). */
    public static final String REASON_ALREADY_HAS_ACCOUNT = "ALREADY_HAS_ACCOUNT";

    private Long studentId;

    private String studentCode;

    private String fullName;

    private String username;

    @ToString.Exclude
    private String tempPassword;

    /** {@value #CREATED} / {@value #SKIPPED}. */
    private String status;

    /** Mã lý do khi {@value #SKIPPED}; {@code null} khi {@value #CREATED}. */
    private String reason;
}
