package com.patorinaldi.wallet.transaction.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull(message = "Source wallet id is required")
        UUID sourceWalletId,
        @NotNull(message = "Destination wallet id is required")
        UUID destinationWalletId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal amount,
        @Size(max = 500) String description,
        @NotNull String idempotencyKey
) {
}
