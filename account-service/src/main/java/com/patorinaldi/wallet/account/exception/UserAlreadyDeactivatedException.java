package com.patorinaldi.wallet.account.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class UserAlreadyDeactivatedException extends RuntimeException {

    private final UUID id;

    public UserAlreadyDeactivatedException(UUID id) {
        super("User already deactivated: " + id);
        this.id = id;
    }
}
