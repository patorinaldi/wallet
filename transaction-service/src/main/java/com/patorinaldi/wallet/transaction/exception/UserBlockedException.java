package com.patorinaldi.wallet.transaction.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class UserBlockedException extends RuntimeException {

    private final UUID userId;
    private final String reason;

    public UserBlockedException(UUID userId, String reason) {
        super("User " + userId + " is blocked: " + reason);
        this.userId = userId;
        this.reason = reason;
    }
}