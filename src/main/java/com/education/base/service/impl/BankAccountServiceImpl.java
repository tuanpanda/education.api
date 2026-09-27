package com.education.base.service.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.BankAccountUpsertRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.entity.BankAccountEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.BankAccountRepository;
import com.education.base.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

    private static final int ACTIVE = 1;
    private static final int INACTIVE = 0;

    private final BankAccountRepository bankAccountRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BankAccountResponseDto> list() {
        List<BankAccountResponseDto> result = new ArrayList<>();
        for (BankAccountEntity entity : bankAccountRepository
                .findByIsDeletedOrderByIsActiveDescIdDesc(PersistenceFlags.NOT_DELETED)) {
            result.add(toDto(entity));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public BankAccountResponseDto getById(Long id) {
        return toDto(requireExisting(id));
    }

    @Override
    @Transactional(readOnly = true)
    public BankAccountResponseDto requireActive() {
        return bankAccountRepository.findByIsActiveAndIsDeleted(ACTIVE, PersistenceFlags.NOT_DELETED)
                .map(this::toDto)
                .orElseThrow(() -> new OracleBusinessException(
                        "BANK_ACCOUNT_NOT_CONFIGURED",
                        "Chưa có số tài khoản đang sử dụng. Vào Cấu hình Hệ thống → Tài khoản thụ hưởng để bật một STK."));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BankAccountResponseDto create(BankAccountUpsertRequest request) {
        BankAccountUpsertRequest payload = requirePayload(request);
        String code = payload.getAccountCode().trim();
        if (bankAccountRepository.existsByAccountCodeAndIsDeleted(code, PersistenceFlags.NOT_DELETED)) {
            throw new OracleBusinessException("BANK_ACCOUNT_CODE_DUPLICATED",
                    "Mã tài khoản '" + code + "' đã tồn tại.");
        }
        String bin = payload.getBankBin().trim();
        String accountNo = payload.getAccountNo().trim();
        if (bankAccountRepository.existsByBankBinAndAccountNoAndIsDeleted(
                bin, accountNo, PersistenceFlags.NOT_DELETED)) {
            throw new OracleBusinessException("BANK_ACCOUNT_DUPLICATED",
                    "Số tài khoản " + accountNo + " (BIN " + bin + ") đã tồn tại.");
        }

        boolean makeActive = Boolean.TRUE.equals(payload.getActive()) || !hasActiveAccount();
        BankAccountEntity entity = BankAccountEntity.builder()
                .accountCode(code)
                .bankBin(bin)
                .bankName(payload.getBankName().trim())
                .accountNo(accountNo)
                .accountName(normalizeAccountName(payload.getAccountName()))
                .note(blankToNull(payload.getNote()))
                .isActive(INACTIVE)
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .build();
        BankAccountEntity saved = bankAccountRepository.saveAndFlush(entity);
        if (makeActive) {
            return activateInternal(saved);
        }
        log.info("Đã tạo STK id={}, code={}, active=false", saved.getId(), saved.getAccountCode());
        return toDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BankAccountResponseDto update(Long id, BankAccountUpsertRequest request) {
        BankAccountUpsertRequest payload = requirePayload(request);
        BankAccountEntity entity = requireExisting(id);
        String code = payload.getAccountCode().trim();
        if (bankAccountRepository.existsByAccountCodeAndIsDeletedAndIdNot(
                code, PersistenceFlags.NOT_DELETED, id)) {
            throw new OracleBusinessException("BANK_ACCOUNT_CODE_DUPLICATED",
                    "Mã tài khoản '" + code + "' đã tồn tại.");
        }
        String bin = payload.getBankBin().trim();
        String accountNo = payload.getAccountNo().trim();
        if (bankAccountRepository.existsByBankBinAndAccountNoAndIsDeletedAndIdNot(
                bin, accountNo, PersistenceFlags.NOT_DELETED, id)) {
            throw new OracleBusinessException("BANK_ACCOUNT_DUPLICATED",
                    "Số tài khoản " + accountNo + " (BIN " + bin + ") đã tồn tại.");
        }

        boolean currentlyActive = isActive(entity);
        boolean wantActive = payload.getActive() == null ? currentlyActive : payload.getActive();
        if (currentlyActive && !wantActive) {
            throw new OracleBusinessException("BANK_ACCOUNT_ONLY_ONE_ACTIVE",
                    "Không thể tắt STK đang sử dụng. Hãy bật một số tài khoản khác trước.");
        }

        entity.setAccountCode(code);
        entity.setBankBin(bin);
        entity.setBankName(payload.getBankName().trim());
        entity.setAccountNo(accountNo);
        entity.setAccountName(normalizeAccountName(payload.getAccountName()));
        entity.setNote(blankToNull(payload.getNote()));
        BankAccountEntity saved = bankAccountRepository.saveAndFlush(entity);
        if (wantActive && !currentlyActive) {
            return activateInternal(saved);
        }
        return toDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BankAccountResponseDto activate(Long id) {
        return activateInternal(requireExisting(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        BankAccountEntity entity = requireExisting(id);
        if (isActive(entity)) {
            throw new OracleBusinessException("BANK_ACCOUNT_IN_USE",
                    "Không thể xóa STK đang sử dụng. Hãy bật một số tài khoản khác trước.");
        }
        entity.setIsDeleted(PersistenceFlags.DELETED);
        entity.setIsActive(INACTIVE);
        bankAccountRepository.save(entity);
        log.info("Đã xóa mềm STK id={}", id);
    }

    private BankAccountResponseDto activateInternal(BankAccountEntity target) {
        try {
            bankAccountRepository.findByIsActiveAndIsDeleted(ACTIVE, PersistenceFlags.NOT_DELETED)
                    .ifPresent(current -> {
                        if (!current.getId().equals(target.getId())) {
                            current.setIsActive(INACTIVE);
                            bankAccountRepository.saveAndFlush(current);
                        }
                    });
            target.setIsActive(ACTIVE);
            BankAccountEntity saved = bankAccountRepository.saveAndFlush(target);
            log.info("STK đang sử dụng: id={}, accountNo={}", saved.getId(), saved.getAccountNo());
            return toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new OracleBusinessException("BANK_ACCOUNT_ONLY_ONE_ACTIVE",
                    "Chỉ được bật một số tài khoản đang sử dụng tại một thời điểm.");
        }
    }

    private boolean hasActiveAccount() {
        return bankAccountRepository.findByIsActiveAndIsDeleted(ACTIVE, PersistenceFlags.NOT_DELETED).isPresent();
    }

    private BankAccountEntity requireExisting(Long id) {
        if (id == null) {
            throw new OracleBusinessException("BANK_ACCOUNT_ID_REQUIRED", "ID tài khoản không được để trống.");
        }
        return bankAccountRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "BANK_ACCOUNT_NOT_FOUND", "Không tìm thấy số tài khoản với ID: " + id));
    }

    private static BankAccountUpsertRequest requirePayload(BankAccountUpsertRequest request) {
        if (request == null) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Thiếu dữ liệu số tài khoản.");
        }
        return request;
    }

    private static boolean isActive(BankAccountEntity entity) {
        return Integer.valueOf(ACTIVE).equals(entity.getIsActive());
    }

    private static String normalizeAccountName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BankAccountResponseDto toDto(BankAccountEntity entity) {
        return BankAccountResponseDto.builder()
                .id(entity.getId())
                .accountCode(entity.getAccountCode())
                .bankBin(entity.getBankBin())
                .bankName(entity.getBankName())
                .accountNo(entity.getAccountNo())
                .accountName(entity.getAccountName())
                .active(isActive(entity))
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
