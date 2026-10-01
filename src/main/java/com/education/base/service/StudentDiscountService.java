package com.education.base.service;

import com.education.base.dto.request.StudentDiscountFilterRequest;
import com.education.base.dto.request.StudentDiscountUpsertRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDiscountDto;
import com.education.base.entity.StudentDiscountEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Miễn giảm - học bổng của học sinh ({@code FIN_STUDENT_DISCOUNTS}, {@code /api/v1/fee-discounts}).
 */
public interface StudentDiscountService {

    PageResponse<StudentDiscountDto> search(StudentDiscountFilterRequest filter);

    StudentDiscountDto getById(Long id);

    StudentDiscountDto create(StudentDiscountUpsertRequest request);

    StudentDiscountDto update(Long id, StudentDiscountUpsertRequest request);

    void softDelete(Long id);

    /**
     * Miễn giảm còn hiệu lực trong kỳ thu {@code [periodStart, periodEnd]} cho lớp {@code classId},
     * gom theo học sinh (một truy vấn cho cả lớp).
     */
    Map<Long, List<StudentDiscountEntity>> findApplicable(Collection<Long> studentIds, Long classId,
                                                          LocalDate periodStart, LocalDate periodEnd);

    /**
     * Tổng tiền giảm của {@code discounts} trên tổng tiền phiếu {@code totalAmount}: cộng dồn
     * {@code PERCENT} (làm tròn đồng) và {@code AMOUNT}, tối đa bằng {@code totalAmount}, không âm.
     */
    BigDecimal computeDiscount(BigDecimal totalAmount, Collection<StudentDiscountEntity> discounts);
}
