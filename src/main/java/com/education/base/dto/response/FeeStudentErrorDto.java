package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Một phần tử {@code data.details} của mọi lỗi 400 / 409 khi sinh phiếu theo gói (contract fee-range-api v1.4 §13,
 * §13.3, §13.6, §13.7; khung thống nhất với FE).
 * <pre>{ studentId, studentName, field, code, message, unbilledCount }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeStudentErrorDto {

    /** {@code null} khi lỗi không gắn với học sinh nào (vd. mảng {@code students} rỗng). */
    private Long studentId;
    private String studentName;

    /** Trường request bị lỗi ({@code students}, {@code studentId}, {@code partialReason}) hoặc {@code null}. */
    private String field;

    /**
     * {@code STUDENTS_REQUIRED}, {@code STUDENT_ID_REQUIRED}, {@code DUPLICATE_STUDENT}, {@code PARTIAL_REASON_REQUIRED},
     * {@code PARTIAL_REASON_TOO_LONG}, {@code NOT_IN_CLASS}, {@code NO_UNBILLED_SESSION}, {@code STUDENT_NOT_ELIGIBLE},
     * {@code FEE_SESSION_CONFLICT}.
     */
    private String code;

    private String message;

    /** Số buổi chưa thu trong khoảng (contract §13: {@code details.unbilledCount}); {@code null} nếu không áp dụng. */
    private Integer unbilledCount;

    /** {@code data} của 400 / 409: {@code { "details": [...] }} - đủ mọi học sinh lỗi. */
    public record Payload(List<FeeStudentErrorDto> details) {
    }
}
