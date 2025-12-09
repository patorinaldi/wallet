package com.patorinaldi.wallet.common.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserBlockedEvent(
        UUID userId,
        UUID triggeredByTransactionId,
        String reason,
        Integer riskScore,
        Instant blockedAt
) {
}
