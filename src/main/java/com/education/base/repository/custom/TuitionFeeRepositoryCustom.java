package com.education.base.repository.custom;

import com.education.base.dto.response.TuitionFeeDetailResponse;

/**
 * Tra cứu chi tiết khoản học phí qua Standalone Procedure {@code PRC_GET_TUITION_FEE_DETAIL}.
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
}
