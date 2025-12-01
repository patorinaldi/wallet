package com.patorinaldi.wallet.transaction.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalRequest(
        @NotNull(message = "A valid wallet id is required")
        UUID walletId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal amount,
        @Size(max = 500)
        String description,
        @NotNull String idempotencyKey
) {
}
