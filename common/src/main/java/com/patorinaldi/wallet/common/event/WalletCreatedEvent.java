package com.patorinaldi.wallet.common.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record WalletCreatedEvent(
        UUID eventId,
        UUID walletId,
        UUID userId,
        String currency,
        Instant createdAt
) {}