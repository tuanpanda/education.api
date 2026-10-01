package com.education.base.service;

import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.TuitionFeeCancelRequest;
import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.dto.request.TuitionFeeUpdateRequest;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionFeeListItemDto;
import com.education.base.dto.response.TuitionQrResponseDto;

public interface TuitionFeeService {

    String MODULE_NAME = "TUITION";

    PageResponse<TuitionFeeListItemDto> search(TuitionFeeFilterRequest filter);

    TuitionFeeDetailResponse getDetail(Long id);

    TuitionFeeDetailResponse create(TuitionFeeCreateRequest request);

    /** Sửa tổng tiền / tiền giảm / hạn thu / ghi chú; trạng thái tính lại bằng {@code FeeStatusCalculator}. */
    TuitionFeeDetailResponse update(Long id, TuitionFeeUpdateRequest request);

    /** Hủy khoản phí ({@code STATUS = CANCELLED}); chỉ khi chưa thu đồng nào ({@code FEE_HAS_PAYMENTS}). */
    TuitionFeeDetailResponse cancel(Long id, TuitionFeeCancelRequest request);

    /** Xóa mềm ({@code IS_DELETED = 1}); chỉ khoản phí {@code UNPAID} chưa có giao dịch ({@code FEE_NOT_DELETABLE}). */
    void delete(Long id);

    TuitionQrResponseDto createQr(Long id, TuitionQrRequest request);

    PaymentTransactionDto confirmPayment(Long id, ConfirmPaymentRequest request);
}
