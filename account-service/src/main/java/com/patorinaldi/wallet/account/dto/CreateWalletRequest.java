package com.patorinaldi.wallet.account.dto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateWalletRequest(
        @NotNull(message = "A valid User ID is required")
        UUID userId,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO 4217 code (e.g., USD, EUR, GBP)")
        String currency
) {
}
