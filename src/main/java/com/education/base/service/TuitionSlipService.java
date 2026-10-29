package com.education.base.service;

import com.education.base.dto.request.GenerateRangeInvoicesRequestDto;
import com.education.base.dto.response.BillableSessionsPreviewDto;
import com.education.base.dto.response.GenerateInvoicesResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TuitionSlipService {

    /** Xem trước buổi tính phí của lớp trong khoảng ngày (chỉ đọc). */
    BillableSessionsPreviewDto previewBillable(Long classId, LocalDate fromDate, LocalDate toDate,
                                               BigDecimal pricePerSession);

    /** Sinh phiếu theo khoảng ngày cho cả lớp, tất cả hoặc không (409 {@code FEE_SESSION_CONFLICT}). */
    GenerateInvoicesResponseDto generateRange(GenerateRangeInvoicesRequestDto request);

    TuitionSlipResponseDto getSlip(Long invoiceId);

    String generateSlipHtml(Long invoiceId);

    String generateSlipHtml(TuitionSlipResponseDto data);
}
