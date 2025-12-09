package com.patorinaldi.wallet.account.exception;

import java.util.UUID;

public class UserBlockedException extends RuntimeException {
    private final UUID userId;
    private final String reason;

    public UserBlockedException(UUID userId, String reason) {
        super("User " + userId + " is blocked: " + reason);
        this.userId = userId;
        this.reason = reason;
    }
}
