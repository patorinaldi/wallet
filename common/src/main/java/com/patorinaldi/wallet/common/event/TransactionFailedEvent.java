package com.patorinaldi.wallet.common.event;

import com.patorinaldi.wallet.common.enums.TransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record TransactionFailedEvent(
        UUID eventId,
        UUID transactionId,
        TransactionType type,
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        String currency,
        UUID relatedTransactionId,
        String description,
        Instant failedAt,
        String errorReason
) {
}
