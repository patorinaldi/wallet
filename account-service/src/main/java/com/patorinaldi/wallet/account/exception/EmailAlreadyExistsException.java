package com.patorinaldi.wallet.account.exception;

import lombok.Getter;

@Getter
public class EmailAlreadyExistsException extends RuntimeException {

    private final String email;

    public EmailAlreadyExistsException(String email) {
        super("Email: " + email + " already exists");
        this.email = email;
    }

}