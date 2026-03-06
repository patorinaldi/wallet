package com.patorinaldi.wallet.common.event.versioned;

/**
 * Marker interface for versioned events.
 * All events that support schema evolution should implement this interface.
 *
 * <p>Schema versioning strategy:
 * <ul>
 *   <li>Version 1: Initial schema</li>
 *   <li>Future versions: Add new optional fields, never remove fields</li>
 *   <li>Breaking changes require new event types</li>
 * </ul>
 *
 * <p>Consumer compatibility:
 * <ul>
 *   <li>Consumers should handle unknown fields gracefully (Jackson default)</li>
 *   <li>Consumers should check schemaVersion() to handle version-specific logic</li>
 * </ul>
 */
public interface VersionedEvent {

    /**
     * Returns the schema version of this event.
     * Used for backward-compatible deserialization and version-specific processing.
     *
     * @return the schema version number (e.g., 1, 2, 3)
     */
    int schemaVersion();

    /**
     * Returns the event type name for routing and logging purposes.
     *
     * @return the event type identifier
     */
    String eventType();
}
