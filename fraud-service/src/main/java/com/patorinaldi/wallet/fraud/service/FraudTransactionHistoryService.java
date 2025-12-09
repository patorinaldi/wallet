package com.patorinaldi.wallet.fraud.service;

import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.fraud.entity.FraudTransactionHistory;
import org.springframework.stereotype.Service;

import com.patorinaldi.wallet.fraud.repository.FraudTransactionHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudTransactionHistoryService {
    
    private final FraudTransactionHistoryRepository fraudTransactionHistoryRepository;

    @Transactional
    public void saveTransaction(TransactionCompletedEvent event) {
        if (fraudTransactionHistoryRepository.existsByTransactionId(event.transactionId())) {
            log.warn("Fraud transaction history for transaction ID: {} already exists. Skipping.", event.transactionId());
            return;
        }

        log.info("Saving fraud transaction history for transaction ID: {}", event.transactionId());

        FraudTransactionHistory fraudEvent = FraudTransactionHistory.builder()
                .transactionId(event.transactionId())
                .walletId(event.walletId())
                .userId(event.userId())
                .amount(event.amount())
                .transactionType(event.type())
                .currency(event.currency())
                .occurredAt(event.completedAt())
                .build();

        fraudTransactionHistoryRepository.save(fraudEvent);
        log.info("Fraud transaction history for transaction ID: {} saved successfully.", event.transactionId());
    }

    public Integer countTransactionsInWindow(UUID walletId, Integer minutes) {
        Integer count = fraudTransactionHistoryRepository.countByWalletIdAndOccurredAtAfter(
                walletId,
                Instant.now().minusSeconds(minutes * 60L)
        );
        if (count == null) {
            count = 0;
        }
        log.debug("Counted {} transactions for wallet ID: {} in the last {} minutes.", count, walletId, minutes);
        return count;
    }

    public boolean isNewWallet(UUID walletId, Integer thresholdMinutes) {
        FraudTransactionHistory firstTransaction = fraudTransactionHistoryRepository.findFirstByWalletIdOrderByOccurredAtAsc(walletId);
        if (firstTransaction == null) {
            log.debug("No transactions found for wallet ID: {}. Considering it as new wallet.", walletId);
            return true;
        }
        Instant thresholdTime = Instant.now().minusSeconds(thresholdMinutes * 60L);
        boolean isNew = firstTransaction.getOccurredAt().isAfter(thresholdTime);
        log.debug("Wallet ID: {} is new: {}", walletId, isNew);
        return isNew;
    }

    public boolean isUnusualAmount(UUID walletId, BigDecimal transactionAmount, BigDecimal multiplier) {
        BigDecimal averageAmount = fraudTransactionHistoryRepository.findAverageAmountByWalletId(walletId);
        if (averageAmount == null || averageAmount.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("No average amount found for wallet ID: {}. Cannot determine unusual amount.", walletId);
            return false;
        }
        BigDecimal threshold = averageAmount.multiply(multiplier);
        boolean isUnusual = transactionAmount.compareTo(threshold) > 0;
        log.debug("Wallet ID: {} has unusual amount: {} (average: {}, base: {}, multiplier: {})",
                  walletId, isUnusual, transactionAmount, averageAmount, threshold);
        return isUnusual;
    }
}
