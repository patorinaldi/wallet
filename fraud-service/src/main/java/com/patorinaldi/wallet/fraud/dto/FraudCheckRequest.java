package com.patorinaldi.wallet.fraud.dto;

import com.patorinaldi.wallet.common.enums.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record FraudCheckRequest(
        @NotNull(message = "Wallet ID is required")
        UUID walletId,

        @NotNull(message = "User ID is required")
        UUID userId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Transaction type is required")
        TransactionType transactionType,

        @NotNull(message = "Currency is required")
        String currency
) {
}
