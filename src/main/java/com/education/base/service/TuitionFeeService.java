package com.education.base.service;

import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionFeeReportDto;
import com.education.base.dto.response.TuitionQrResponseDto;

public interface TuitionFeeService {

    String MODULE_NAME = "TUITION";

    PageResponse<TuitionFeeReportDto> search(TuitionFeeFilterRequest filter);

    TuitionFeeDetailResponse getDetail(Long id);

    TuitionFeeDetailResponse create(TuitionFeeCreateRequest request);

    TuitionQrResponseDto createQr(Long id, TuitionQrRequest request);

    PaymentTransactionDto confirmPayment(Long id, ConfirmPaymentRequest request);
}
