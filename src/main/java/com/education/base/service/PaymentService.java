package com.education.base.service;

import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.response.PaymentTransactionDto;

/**
 * Nghiệp vụ giao dịch thanh toán học phí ({@code FIN_PAYMENT_TRANSACTIONS}): xác nhận thanh toán,
 * tra cứu giao dịch, phiếu thu, hủy và hoàn tiền.
 * <p>
 * Đây là nơi duy nhất ghi giao dịch và cập nhật {@code PAID_AMOUNT} / {@code STATUS} của khoản học phí
 * tương ứng. {@code TuitionFeeService#confirmPayment} chỉ ủy quyền sang {@link #confirmPayment}.
 */
public interface PaymentService {

    /**
     * Xác nhận một khoản thanh toán đã nhận được cho khoản học phí {@code feeId}.
     * Khoản phí bị khóa dòng ({@code SELECT ... FOR UPDATE}) trong suốt transaction để chống ghi đồng thời.
     *
     * @param feeId   ID khoản học phí
     * @param request số tiền (bỏ trống = thu toàn bộ số còn lại), hình thức, mã giao dịch, mã tham chiếu ngân hàng
     * @return giao dịch vừa ghi nhận
     */
    PaymentTransactionDto confirmPayment(Long feeId, ConfirmPaymentRequest request);
}
