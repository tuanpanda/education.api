package com.education.base.repository;

import com.education.base.entity.BankAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccountEntity, Long> {

    Optional<BankAccountEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    Optional<BankAccountEntity> findByIsActiveAndIsDeleted(Integer isActive, Integer isDeleted);

    List<BankAccountEntity> findByIsDeletedOrderByIsActiveDescIdDesc(Integer isDeleted);

    boolean existsByAccountCodeAndIsDeleted(String accountCode, Integer isDeleted);

    boolean existsByAccountCodeAndIsDeletedAndIdNot(String accountCode, Integer isDeleted, Long id);

    boolean existsByBankBinAndAccountNoAndIsDeleted(String bankBin, String accountNo, Integer isDeleted);

    boolean existsByBankBinAndAccountNoAndIsDeletedAndIdNot(
            String bankBin, String accountNo, Integer isDeleted, Long id);
}
