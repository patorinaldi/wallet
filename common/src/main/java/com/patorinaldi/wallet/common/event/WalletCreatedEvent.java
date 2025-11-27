package com.patorinaldi.wallet.common.event;

import java.time.Instant;
import java.util.UUID;

public record WalletCreatedEvent(
        UUID eventId,
        UUID walletId,
        UUID userId,
        String currency,
        Instant createdAt
) {}