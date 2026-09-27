package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mã lớp dự kiến theo {@code SYS_CODE_RULES} / {@code FN_NEXT_BIZ_CODE}.
 * Đây là bản xem trước, chưa tăng {@code LAST_SEQ}; mã thật được trigger cấp lúc INSERT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NextClassCodeResponse {

    private String classCode;
    private Integer gradeLevel;
    private boolean preview;
}
