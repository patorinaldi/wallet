package com.patorinaldi.wallet.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID walletId,
        UUID userId,
        Instant createdAt,
        boolean active,
        String currency
) {}
