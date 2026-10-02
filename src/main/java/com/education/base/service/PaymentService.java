package com.education.base.service;

import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.PaymentTransactionFilterRequest;
import com.education.base.dto.request.RefundTransactionRequest;
import com.education.base.dto.request.VoidTransactionRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDetailResponse;
import com.education.base.dto.response.PaymentTransactionDto;

/**
 * Nghiệp vụ giao dịch thanh toán học phí ({@code FIN_PAYMENT_TRANSACTIONS}): xác nhận thanh toán,
 * tra cứu giao dịch, phiếu thu, hủy và hoàn tiền.
 * <p>
 * Đây là nơi duy nhất ghi giao dịch và cập nhật {@code PAID_AMOUNT} / {@code STATUS} của khoản học phí
 * tương ứng. {@code TuitionFeeService#confirmPayment} chỉ ủy quyền sang {@link #confirmPayment}.
 * Mọi thao tác ghi đều khóa dòng khoản học phí ({@code findByIdAndIsDeletedForUpdate}) trước.
 */
public interface PaymentService {

    /**
     * Xác nhận một khoản thanh toán đã nhận được cho khoản học phí {@code feeId}.
     * Khoản phí bị khóa dòng ({@code SELECT ... FOR UPDATE}) trong suốt transaction để chống ghi đồng thời.
     * Giao dịch được cấp số phiếu thu ({@code RECEIPT_NO}) và lưu người nộp ({@code PAYER_NAME}).
     *
     * @param feeId   ID khoản học phí
     * @param request số tiền (bỏ trống = thu toàn bộ số còn lại), hình thức, mã giao dịch, mã tham chiếu ngân hàng
     * @return giao dịch vừa ghi nhận
     */
    PaymentTransactionDto confirmPayment(Long feeId, ConfirmPaymentRequest request);

    /** Tra cứu giao dịch có phân trang, mặc định mới nhất trước ({@code PAYMENT_DATE} giảm dần). */
    PageResponse<PaymentTransactionDto> search(PaymentTransactionFilterRequest filter);

    /** Chi tiết giao dịch kèm tóm tắt khoản phí và các lần hoàn tiền. */
    PaymentTransactionDetailResponse getDetail(Long id);

    /** HTML phiếu thu / phiếu chi để in. */
    String renderReceiptHtml(Long id);

    /**
     * Hủy một giao dịch thu {@code SUCCESS} chưa có lần hoàn tiền nào còn hiệu lực; tính lại
     * {@code PAID_AMOUNT} / {@code STATUS} của khoản phí.
     */
    PaymentTransactionDto voidTransaction(Long id, VoidTransactionRequest request);

    /**
     * Hoàn tiền (một phần / toàn bộ) cho giao dịch thu {@code SUCCESS}: tạo dòng {@code REFUND}, tính lại khoản phí;
     * hoàn đủ thì giao dịch gốc chuyển {@code REFUNDED}.
     *
     * @return dòng hoàn tiền vừa tạo
     */
    PaymentTransactionDto refundTransaction(Long id, RefundTransactionRequest request);
}
