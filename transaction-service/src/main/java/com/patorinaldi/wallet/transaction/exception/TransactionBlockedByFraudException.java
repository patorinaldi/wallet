package com.patorinaldi.wallet.transaction.exception;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class TransactionBlockedByFraudException extends RuntimeException {

    private final UUID walletId;
    private final int riskScore;
    private final List<String> triggeredRules;

    public TransactionBlockedByFraudException(UUID walletId, int riskScore, List<String> triggeredRules) {
        super(String.format("Transaction blocked by fraud detection for wallet %s. Risk score: %d, Triggered rules: %s",
                walletId, riskScore, String.join(", ", triggeredRules)));
        this.walletId = walletId;
        this.riskScore = riskScore;
        this.triggeredRules = triggeredRules;
    }
}
