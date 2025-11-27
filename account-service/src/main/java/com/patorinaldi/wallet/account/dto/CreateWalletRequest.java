package com.patorinaldi.wallet.account.dto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWalletRequest(
        @NotNull
        UUID userId,

        @Size(min = 3, max = 3, message = "Currency must be 3 characters")
        String currency
) {
}
