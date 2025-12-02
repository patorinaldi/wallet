package com.patorinaldi.wallet.transaction.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class TransactionNotFoundException extends RuntimeException {

    private final UUID id;

    public TransactionNotFoundException(UUID id) {
        super(String.format("Transaction not found for id: %s", id));
        this.id = id;
    }
}
