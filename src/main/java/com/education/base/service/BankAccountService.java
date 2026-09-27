package com.education.base.service;

import com.education.base.dto.request.BankAccountUpsertRequest;
import com.education.base.dto.response.BankAccountResponseDto;

import java.util.List;

public interface BankAccountService {

    List<BankAccountResponseDto> list();

    BankAccountResponseDto getById(Long id);

    /** STK đang sử dụng để sinh VietQR / phiếu học phí. */
    BankAccountResponseDto requireActive();

    BankAccountResponseDto create(BankAccountUpsertRequest request);

    BankAccountResponseDto update(Long id, BankAccountUpsertRequest request);

    /** Đặt STK này là đang dùng; các STK khác sẽ tắt. */
    BankAccountResponseDto activate(Long id);

    void softDelete(Long id);
}
