package com.patorinaldi.wallet.common.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserRegisteredEvent(
   UUID recordId,
   UUID userId,
   String email,
   String fullName,
   Instant registeredAt
) {}
