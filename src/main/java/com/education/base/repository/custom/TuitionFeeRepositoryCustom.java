package com.education.base.repository.custom;

import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionSlipResponseDto;

/**
 * Tra cứu chi tiết khoản học phí và phiếu học phí điện tử qua Standalone Procedure.
 */
public interface TuitionFeeRepositoryCustom {

    /**
     * Lấy thông tin khoản học phí kèm lịch sử giao dịch.
     * Danh sách tài liệu đính kèm do tầng Service nạp riêng qua {@code PRC_GET_FILES_BY_REF}.
     *
     * @param tuitionFeeId ID khoản học phí.
     * @return chi tiết học phí; không bao giờ {@code null} nếu procedure trả {@code O_ERR_CODE = 0}.
     */
    TuitionFeeDetailResponse getFeeDetail(Long tuitionFeeId);

    /**
     * Đọc phiếu học phí từ {@code PRC_GET_TUITION_SLIP_DATA}: thông tin hóa đơn
     * và danh sách ngày {@code PRESENT} trong kỳ thu.
     *
     * @param invoiceId ID khoản học phí / phiếu.
     * @return phiếu chưa gồm mã VietQR (tầng Service bổ sung).
     */
    TuitionSlipResponseDto getTuitionSlipData(Long invoiceId);

    /**
     * Sinh mã khoản phí tiếp theo theo {@code SYS_CODE_RULES} / {@code FN_NEXT_BIZ_CODE('TUITION')}.
     */
    String nextTuitionFeeCode();
}
