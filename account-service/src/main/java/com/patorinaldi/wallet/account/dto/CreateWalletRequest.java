package com.patorinaldi.wallet.account.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UUID;

public record CreateWalletRequest(
        @NotNull
        UUID userId,

        @Size(min = 3, max = 3, message = "Currency must be 3 characters")
        String currency
) {
}
