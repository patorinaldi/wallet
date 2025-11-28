package com.patorinaldi.wallet.account.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class WalletAlreadyDeactivatedException extends RuntimeException {

    private final UUID id;

    public WalletAlreadyDeactivatedException(UUID id) {
        super("Wallet already deactivated: " + id);
        this.id = id;
    }
}
