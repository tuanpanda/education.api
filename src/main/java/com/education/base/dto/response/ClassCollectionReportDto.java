package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Báo cáo thu tiền theo lớp của một năm / tháng, kèm dòng tổng cộng (tính ở tầng Service).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassCollectionReportDto {

    private Integer year;

    /** Tháng 1-12; null là cả năm. */
    private Integer month;

    @Builder.Default
    private List<ClassCollectionDto> rows = new ArrayList<>();

    /** Tổng cộng các lớp (classId = null, collectionRate tính lại trên tổng). */
    private ClassCollectionDto total;
}
