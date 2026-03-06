package com.patorinaldi.wallet.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patorinaldi.wallet.common.event.versioned.VersionedEvent;
import lombok.Builder;

import java.util.UUID;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record FraudAlertEvent(
        UUID analysisId,
        UUID transactionId,
        Integer riskScore,
        String decision,
        int schemaVersion
) implements VersionedEvent {

    private static final int CURRENT_VERSION = 1;
    private static final String EVENT_TYPE = "fraud-alert";

    public FraudAlertEvent {
        if (schemaVersion == 0) {
            schemaVersion = CURRENT_VERSION;
        }
    }

    @Override
    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}