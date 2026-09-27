package com.education.base.service.impl;

import com.education.base.dto.request.BankAccountUpsertRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.entity.BankAccountEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.BankAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceImplTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    private BankAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BankAccountServiceImpl(bankAccountRepository);
    }

    @Test
    void create_firstAccount_becomesActiveEvenIfFlagFalse() {
        when(bankAccountRepository.existsByAccountCodeAndIsDeleted("BA01", 0)).thenReturn(false);
        when(bankAccountRepository.existsByBankBinAndAccountNoAndIsDeleted("970436", "111", 0)).thenReturn(false);
        when(bankAccountRepository.findByIsActiveAndIsDeleted(1, 0)).thenReturn(Optional.empty());
        when(bankAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            BankAccountEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        BankAccountResponseDto dto = service.create(sample("BA01", "111", false));
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getAccountName()).isEqualTo("TRUONG EDUCATION");
    }

    @Test
    void activate_turnsOffPreviousActive() {
        BankAccountEntity current = entity(1L, "BA01", "111", 1);
        BankAccountEntity next = entity(2L, "BA02", "222", 0);
        when(bankAccountRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(next));
        when(bankAccountRepository.findByIsActiveAndIsDeleted(1, 0)).thenReturn(Optional.of(current));
        when(bankAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BankAccountResponseDto dto = service.activate(2L);
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getId()).isEqualTo(2L);

        ArgumentCaptor<BankAccountEntity> captor = ArgumentCaptor.forClass(BankAccountEntity.class);
        verify(bankAccountRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        List<BankAccountEntity> saved = captor.getAllValues();
        assertThat(saved.stream().anyMatch(e -> e.getId() == 1L && e.getIsActive() == 0)).isTrue();
        assertThat(saved.getLast().getId()).isEqualTo(2L);
        assertThat(saved.getLast().getIsActive()).isEqualTo(1);
    }

    @Test
    void update_cannotDeactivateTheOnlyActiveAccount() {
        BankAccountEntity current = entity(1L, "BA01", "111", 1);
        when(bankAccountRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(current));
        when(bankAccountRepository.existsByAccountCodeAndIsDeletedAndIdNot("BA01", 0, 1L)).thenReturn(false);
        when(bankAccountRepository.existsByBankBinAndAccountNoAndIsDeletedAndIdNot(
                "970436", "111", 0, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.update(1L, sample("BA01", "111", false)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("BANK_ACCOUNT_ONLY_ONE_ACTIVE");
        verify(bankAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void softDelete_activeAccount_throws() {
        when(bankAccountRepository.findByIdAndIsDeleted(1L, 0))
                .thenReturn(Optional.of(entity(1L, "BA01", "111", 1)));

        assertThatThrownBy(() -> service.softDelete(1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("BANK_ACCOUNT_IN_USE");
    }

    @Test
    void requireActive_whenMissing_throws() {
        when(bankAccountRepository.findByIsActiveAndIsDeleted(1, 0)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireActive())
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode")
                .isEqualTo("BANK_ACCOUNT_NOT_CONFIGURED");
    }

    private static BankAccountUpsertRequest sample(String code, String accountNo, boolean active) {
        return BankAccountUpsertRequest.builder()
                .accountCode(code)
                .bankBin("970436")
                .bankName("Vietcombank")
                .accountNo(accountNo)
                .accountName("truong education")
                .active(active)
                .build();
    }

    private static BankAccountEntity entity(Long id, String code, String accountNo, int active) {
        return BankAccountEntity.builder()
                .id(id)
                .accountCode(code)
                .bankBin("970436")
                .bankName("Vietcombank")
                .accountNo(accountNo)
                .accountName("TRUONG EDUCATION")
                .isActive(active)
                .isDeleted(0)
                .build();
    }
}
