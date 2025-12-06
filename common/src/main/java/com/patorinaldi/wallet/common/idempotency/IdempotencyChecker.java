package com.patorinaldi.wallet.common.idempotency;

public interface IdempotencyChecker {

    /**
     * Check if event was already processed.
     * @param eventId Unique identifier for the event
     * @return true if already processed, false if new
     */
    boolean isProcessed(String eventId);

    /**
     * Mark event as processed.
     * @param eventId Unique identifier for the event
     */
    void markProcessed(String eventId);

    /**
     * Mark event as processed with outcome tracking.
     */
    void markProcessed(String eventId, ProcessingOutcome outcome);
}
