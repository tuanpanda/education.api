package com.education.base.service;

import com.education.base.dto.request.CreateMonthlyInvoiceRequestDto;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;

public interface TuitionSlipService {

    GenerateMonthlyInvoicesResponseDto generateMonthly(CreateMonthlyInvoiceRequestDto request);

    TuitionSlipResponseDto getSlip(Long invoiceId);

    String generateSlipHtml(Long invoiceId);

    String generateSlipHtml(TuitionSlipResponseDto data);
}
