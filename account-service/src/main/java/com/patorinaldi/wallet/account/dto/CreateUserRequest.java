package com.patorinaldi.wallet.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(

        @Email(message = "Invalid email format.")
        @NotBlank(message = "A valid email is required.")
        String email,
        @NotBlank(message = "A full name is required.")
        String fullName,
        String phoneNumber
){}
