package com.patorinaldi.wallet.common.idempotency;

import java.time.Instant;

public record ProcessingOutcome(
    boolean success,
    String errorMessage,
    Instant processedAt
) {}
