package com.patorinaldi.wallet.transaction.dto;

import com.patorinaldi.wallet.common.enums.TransactionStatus;
import com.patorinaldi.wallet.common.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        TransactionStatus status,
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt,
        Instant completedAt,
        String errorMessage,
        UUID relatedWalletId,
        UUID relatedTransactionId
) {
}
