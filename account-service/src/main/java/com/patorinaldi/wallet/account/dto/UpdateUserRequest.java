package com.patorinaldi.wallet.account.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
        @Email(message = "Invalid email format.")
        String email,
        String fullName,
        String phoneNumber
) {
}
