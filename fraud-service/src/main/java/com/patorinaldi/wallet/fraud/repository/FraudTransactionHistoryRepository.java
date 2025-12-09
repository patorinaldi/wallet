package com.patorinaldi.wallet.fraud.repository;

import com.patorinaldi.wallet.fraud.entity.FraudTransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface FraudTransactionHistoryRepository extends JpaRepository<FraudTransactionHistory, UUID> {

    Integer countByWalletIdAndOccurredAtAfter(UUID walletId, Instant since);

    FraudTransactionHistory findFirstByWalletIdOrderByOccurredAtAsc(UUID walletId);

    @Query("SELECT AVG(f.amount) FROM FraudTransactionHistory f WHERE f.walletId = :walletId")
    BigDecimal findAverageAmountByWalletId(UUID walletId);

    boolean existsByTransactionId(UUID transactionId);

}
