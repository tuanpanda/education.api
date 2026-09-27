package com.education.base.repository;

import com.education.base.entity.CodeRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository JPA của {@code SYS_CODE_RULES}. Việc cấp mã kế tiếp do Function
 * {@code FN_NEXT_BIZ_CODE} thực hiện trong Database (khóa hàng FOR UPDATE).
 */
public interface CodeRuleRepository extends JpaRepository<CodeRuleEntity, Long> {

    Optional<CodeRuleEntity> findByRuleCodeAndIsDeleted(String ruleCode, Integer isDeleted);
}
