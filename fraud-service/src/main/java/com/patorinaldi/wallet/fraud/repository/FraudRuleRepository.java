package com.patorinaldi.wallet.fraud.repository;

import com.patorinaldi.wallet.fraud.entity.FraudRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {

    List<FraudRule> findByActiveTrue();

    boolean existsByRuleCode(String ruleCode);

}
