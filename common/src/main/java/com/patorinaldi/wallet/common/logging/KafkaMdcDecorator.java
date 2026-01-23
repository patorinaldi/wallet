package com.patorinaldi.wallet.common.logging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility class for adding context to MDC during Kafka event processing.
 * This enables correlation of logs across event-driven processing.
 */
public final class KafkaMdcDecorator {

    public static final String TRANSACTION_ID_MDC_KEY = "transactionId";
    public static final String USER_ID_MDC_KEY = "userId";
    public static final String EVENT_TYPE_MDC_KEY = "eventType";

    private KafkaMdcDecorator() {
        // Utility class - prevent instantiation
    }

    /**
     * Execute an action with transaction and user context in MDC.
     *
     * @param transactionId the transaction ID (nullable)
     * @param userId the user ID (nullable)
     * @param action the action to execute
     */
    public static void withMdc(UUID transactionId, UUID userId, Runnable action) {
        try {
            if (transactionId != null) {
                MDC.put(TRANSACTION_ID_MDC_KEY, transactionId.toString());
            }
            if (userId != null) {
                MDC.put(USER_ID_MDC_KEY, userId.toString());
            }
            action.run();
        } finally {
            MDC.remove(TRANSACTION_ID_MDC_KEY);
            MDC.remove(USER_ID_MDC_KEY);
        }
    }

    /**
     * Execute an action with full event context in MDC.
     *
     * @param transactionId the transaction ID (nullable)
     * @param userId the user ID (nullable)
     * @param eventType the event type name (nullable)
     * @param action the action to execute
     */
    public static void withMdc(UUID transactionId, UUID userId, String eventType, Runnable action) {
        try {
            if (transactionId != null) {
                MDC.put(TRANSACTION_ID_MDC_KEY, transactionId.toString());
            }
            if (userId != null) {
                MDC.put(USER_ID_MDC_KEY, userId.toString());
            }
            if (eventType != null) {
                MDC.put(EVENT_TYPE_MDC_KEY, eventType);
            }
            action.run();
        } finally {
            MDC.remove(TRANSACTION_ID_MDC_KEY);
            MDC.remove(USER_ID_MDC_KEY);
            MDC.remove(EVENT_TYPE_MDC_KEY);
        }
    }
}
