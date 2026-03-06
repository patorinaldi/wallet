package com.patorinaldi.wallet.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.versioned.VersionedEvent;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCompletedEvent(
        UUID eventId,
        UUID transactionId,
        TransactionType type,
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        UUID relatedWalletId,
        UUID relatedTransactionId,
        String description,
        Instant completedAt,
        int schemaVersion
) implements VersionedEvent {

    private static final int CURRENT_VERSION = 1;
    private static final String EVENT_TYPE = "transaction-completed";

    public TransactionCompletedEvent {
        if (schemaVersion == 0) {
            schemaVersion = CURRENT_VERSION;
        }
    }

    @Override
    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
