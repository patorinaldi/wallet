package com.patorinaldi.wallet.account.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class WalletAlreadyExistsException extends RuntimeException {

    private final UUID userId;
    private final String currency;

    public WalletAlreadyExistsException(UUID userId, String currency) {
        super("User " + userId + " already has a wallet with currency: " + currency);
        this.userId = userId;
        this.currency = currency;
    }

}
