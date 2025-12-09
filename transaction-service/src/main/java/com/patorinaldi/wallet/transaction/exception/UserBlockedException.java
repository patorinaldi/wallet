package com.patorinaldi.wallet.transaction.exception;

import java.util.UUID;

public class UserBlockedException extends RuntimeException {

  private final UUID userId;

  public UserBlockedException(UUID userId, String reason) {
    super("User " + userId + " is blocked: " + reason);
    this.userId = userId;
  }

  public UUID getUserId() {
    return userId;
  }
}