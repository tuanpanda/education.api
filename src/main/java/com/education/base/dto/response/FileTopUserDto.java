package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top người upload / học sinh theo dung lượng. Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileTopUserDto {

    /** {@code SYS_USERS.ID} (top người upload) hoặc {@code EDU_STUDENTS.ID} (top học sinh). */
    private Long id;
    /** Tên đăng nhập hoặc mã học sinh. */
    private String code;
    private String fullName;
    private long fileCount;
    private long totalBytes;
    /** Quota áp dụng (byte) - chỉ với học sinh; NULL nếu không áp dụng. */
    private Long quotaBytes;
}
