package com.patorinaldi.wallet.transaction.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositRequest(
        @NotNull(message = "A valid wallet id is required")
        UUID walletId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal amount,
        @Size(max = 500) String description,
        @NotNull String idempotencyKey
) {
}
