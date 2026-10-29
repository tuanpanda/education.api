package com.education.base.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Xem trước phiếu theo khoảng ngày ({@code GET /api/v1/tuition-fees/billable-sessions}), chỉ đọc.
 * <p>
 * Không có {@code pricePerSession} thì mọi trường tiền là {@code null} (vẫn có trong JSON - {@link JsonInclude.Include#ALWAYS}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class BillableSessionsPreviewDto {

    private Long classId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal pricePerSession;

    @Builder.Default
    private List<StudentRow> students = new ArrayList<>();

    private Totals totals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class StudentRow {
        private Long studentId;
        private String studentCode;
        private String studentName;
        @Builder.Default
        private List<LocalDate> sessionDates = new ArrayList<>();
        private int sessionCount;
        private BigDecimal totalAmount;
        private BigDecimal discountAmount;
        private BigDecimal netAmount;
        @Builder.Default
        private List<LocalDate> conflictDates = new ArrayList<>();
        @Builder.Default
        private List<String> conflictFeeCodes = new ArrayList<>();
        /** Học sinh đã nghỉ lớp nhưng có buổi trong khoảng: vẫn được thu. */
        private boolean inactive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Totals {
        private int studentCount;
        private int billableStudentCount;
        private int skippedNoAttendance;
        private int sessionCount;
        private BigDecimal totalAmount;
        private BigDecimal discountAmount;
        private BigDecimal netAmount;
        private int conflictStudentCount;
    }
}
