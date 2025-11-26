package com.patorinaldi.wallet.common.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
   UUID recordId,
   UUID userId,
   String email,
   String fullName,
   Instant registeredAt
) {}
