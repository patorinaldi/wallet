package com.patorinaldi.wallet.notification.exception;

public class RetryableException extends RuntimeException {

    public RetryableException(String message) {
        super(message);
    }
}
