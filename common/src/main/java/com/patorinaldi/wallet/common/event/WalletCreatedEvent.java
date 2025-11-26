package com.patorinaldi.wallet.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletCreatedEvent(
        UUID eventId,
        UUID walletId,
        UUID userId,
        String currency,
        BigDecimal initialBalance,
        Instant createdAt
) {}