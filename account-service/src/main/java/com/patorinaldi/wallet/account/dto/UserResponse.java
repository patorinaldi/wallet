package com.patorinaldi.wallet.account.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
   UUID id,
   String email,
   String fullName,
   String phoneNumber,
   Instant createdAt,
   boolean active
) {}
