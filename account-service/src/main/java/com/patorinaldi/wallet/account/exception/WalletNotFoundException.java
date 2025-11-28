package com.patorinaldi.wallet.account.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class WalletNotFoundException extends RuntimeException {

    private final UUID id;

    public WalletNotFoundException(UUID id) {
        super("Wallet not found with ID: " + id);
        this.id = id;
    }

}
