package com.patorinaldi.wallet.common.event;

import lombok.Builder;

import java.util.UUID;

@Builder
public record FraudAlertEvent(
        UUID analysisId,
        UUID transactionId,
        Integer riskScore,
        String decision
)
{}