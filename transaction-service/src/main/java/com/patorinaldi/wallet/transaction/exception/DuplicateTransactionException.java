package com.patorinaldi.wallet.transaction.exception;

import lombok.Getter;

@Getter
public class DuplicateTransactionException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateTransactionException(String idempotencyKey) {
        super(String.format("Transaction already exists with idempotency key: %s", idempotencyKey));
        this.idempotencyKey = idempotencyKey;
    }
}