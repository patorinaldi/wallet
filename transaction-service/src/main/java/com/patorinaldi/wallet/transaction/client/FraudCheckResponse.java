package com.patorinaldi.wallet.transaction.client;

import java.util.List;

public record FraudCheckResponse(
        int riskScore,
        String decision,
        List<String> triggeredRules,
        String message
) {
    public boolean isBlocked() {
        return "BLOCK".equals(decision);
    }

    public boolean isFlagged() {
        return "FLAG".equals(decision);
    }

    public boolean isApproved() {
        return "APPROVE".equals(decision);
    }
}
