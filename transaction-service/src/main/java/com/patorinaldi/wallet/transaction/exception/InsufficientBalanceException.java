package com.patorinaldi.wallet.transaction.exception;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class InsufficientBalanceException extends RuntimeException {

    private final UUID walletId;
    private final BigDecimal currentBalance;
    private final BigDecimal requestedAmount;

    public InsufficientBalanceException(UUID walletId, BigDecimal currentBalance, BigDecimal requestedAmount) {
        super(String.format(
                "Insufficient balance in wallet %s. Current balance: %s, requested: %s",
                walletId,
                currentBalance,
                requestedAmount
        ));
        this.walletId = walletId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
}