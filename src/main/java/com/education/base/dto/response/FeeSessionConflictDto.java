package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Một học sinh có buổi đã nằm trên phiếu khác (409 {@code FEE_SESSION_CONFLICT}, phần tử của {@code data.details}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeSessionConflictDto {

    private Long studentId;
    private String studentCode;
    private String studentName;

    /** Ngày bị trùng, tăng dần. */
    @Builder.Default
    private List<LocalDate> dates = new ArrayList<>();

    /** Mã phiếu đang giữ các ngày đó. */
    @Builder.Default
    private List<String> feeCodes = new ArrayList<>();

    /** {@code data} của 409: {@code { "details": [...] }} (rỗng khi lỗi đến từ ORA-00001). */
    public record Payload(List<FeeSessionConflictDto> details) {
    }
}
