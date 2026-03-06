package com.patorinaldi.wallet.fraud.dto;

import java.util.List;

public record FraudCheckResponse(
        int riskScore,
        String decision,
        List<String> triggeredRules,
        String message
) {
    public static FraudCheckResponse approve(int riskScore, List<String> triggeredRules) {
        return new FraudCheckResponse(riskScore, "APPROVE", triggeredRules, "Transaction approved");
    }

    public static FraudCheckResponse flag(int riskScore, List<String> triggeredRules) {
        return new FraudCheckResponse(riskScore, "FLAG", triggeredRules, "Transaction flagged for review");
    }

    public static FraudCheckResponse block(int riskScore, List<String> triggeredRules) {
        return new FraudCheckResponse(riskScore, "BLOCK", triggeredRules, "Transaction blocked due to high risk");
    }
}
