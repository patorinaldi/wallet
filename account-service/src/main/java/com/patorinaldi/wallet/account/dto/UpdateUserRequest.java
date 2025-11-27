package com.patorinaldi.wallet.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public record UpdateUserRequest(
        @Email(message = "Invalid email format.")
        String email,
        String fullName,
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format. Use E.164 format (e.g., +1234567890)")
        String phoneNumber
) {
}
