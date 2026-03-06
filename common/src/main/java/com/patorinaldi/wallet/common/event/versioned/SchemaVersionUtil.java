package com.patorinaldi.wallet.common.event.versioned;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for handling schema version checks and migrations.
 *
 * <p>Usage guidelines:
 * <ul>
 *   <li>Use {@link #isCompatible} to check if an event version is supported</li>
 *   <li>Log warnings for unknown versions but continue processing</li>
 *   <li>Add migration logic when version-specific handling is needed</li>
 * </ul>
 */
@Slf4j
public final class SchemaVersionUtil {

    private SchemaVersionUtil() {
        // Utility class
    }

    /**
     * Checks if the event schema version is compatible with the current consumer.
     * Returns true for all known versions (currently only v1).
     *
     * @param event the versioned event to check
     * @param maxSupportedVersion the maximum version this consumer supports
     * @return true if the event can be processed
     */
    public static boolean isCompatible(VersionedEvent event, int maxSupportedVersion) {
        if (event.schemaVersion() > maxSupportedVersion) {
            log.warn("Event {} has schema version {} which is newer than supported version {}. " +
                            "Processing will continue but some fields may be ignored.",
                    event.eventType(), event.schemaVersion(), maxSupportedVersion);
        }
        return true; // Always process - newer versions should be backward compatible
    }

    /**
     * Logs event processing with version information for debugging.
     *
     * @param event the event being processed
     * @param consumerName the name of the consumer processing the event
     */
    public static void logEventProcessing(VersionedEvent event, String consumerName) {
        log.debug("Processing {} v{} in consumer {}",
                event.eventType(), event.schemaVersion(), consumerName);
    }

    /**
     * Returns whether the event is at the expected version.
     *
     * @param event the event to check
     * @param expectedVersion the expected schema version
     * @return true if versions match
     */
    public static boolean isVersion(VersionedEvent event, int expectedVersion) {
        return event.schemaVersion() == expectedVersion;
    }
}
