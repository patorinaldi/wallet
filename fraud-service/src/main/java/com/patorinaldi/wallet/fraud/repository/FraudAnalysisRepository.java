package com.patorinaldi.wallet.fraud.repository;

import com.patorinaldi.wallet.fraud.entity.FraudAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FraudAnalysisRepository extends JpaRepository<FraudAnalysis, UUID> {

    boolean existsByTransactionId(UUID transactionId);

    Optional<FraudAnalysis> findByTransactionId(UUID transactionId);

}
