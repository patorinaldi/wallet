package com.patorinaldi.wallet.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID walletId,
        UUID userId,
        BigDecimal balance,
        String currency,
        Instant lastUpdated
) {}