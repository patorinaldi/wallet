package com.patorinaldi.wallet.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.patorinaldi.wallet.common.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VersionedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void transactionCompletedEvent_shouldHaveDefaultSchemaVersion() {
        // When
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("100.00"))
                .completedAt(Instant.now())
                .build();

        // Then
        assertEquals(1, event.schemaVersion());
        assertEquals("transaction-completed", event.eventType());
    }

    @Test
    void userRegisteredEvent_shouldHaveDefaultSchemaVersion() {
        // When
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .recordId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .registeredAt(Instant.now())
                .build();

        // Then
        assertEquals(1, event.schemaVersion());
        assertEquals("user-registered", event.eventType());
    }

    @Test
    void walletCreatedEvent_shouldHaveDefaultSchemaVersion() {
        // When
        WalletCreatedEvent event = WalletCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .currency("USD")
                .createdAt(Instant.now())
                .build();

        // Then
        assertEquals(1, event.schemaVersion());
        assertEquals("wallet-created", event.eventType());
    }

    @Test
    void userBlockedEvent_shouldHaveDefaultSchemaVersion() {
        // When
        UserBlockedEvent event = UserBlockedEvent.builder()
                .userId(UUID.randomUUID())
                .triggeredByTransactionId(UUID.randomUUID())
                .reason("Fraud detected")
                .riskScore(85)
                .blockedAt(Instant.now())
                .build();

        // Then
        assertEquals(1, event.schemaVersion());
        assertEquals("user-blocked", event.eventType());
    }

    @Test
    void fraudAlertEvent_shouldHaveDefaultSchemaVersion() {
        // When
        FraudAlertEvent event = FraudAlertEvent.builder()
                .analysisId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .riskScore(75)
                .decision("FLAG")
                .build();

        // Then
        assertEquals(1, event.schemaVersion());
        assertEquals("fraud-alert", event.eventType());
    }

    @Test
    void transactionFailedEvent_shouldHaveDefaultSchemaVersion() {
        // When
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.WITHDRAWAL)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .failedAt(Instant.now())
                .errorReason("Insufficient balance")
                .build();

        // Then
        assertEquals(1, event.schemaVersion());
        assertEquals("transaction-failed", event.eventType());
    }

    @Test
    void transactionCompletedEvent_shouldSerializeWithSchemaVersion() throws Exception {
        // Given
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("100.00"))
                .completedAt(Instant.now())
                .build();

        // When
        String json = objectMapper.writeValueAsString(event);

        // Then
        assertTrue(json.contains("\"schemaVersion\":1"));
    }

    @Test
    void transactionCompletedEvent_shouldDeserializeWithSchemaVersion() throws Exception {
        // Given
        String json = """
                {
                    "eventId": "123e4567-e89b-12d3-a456-426614174000",
                    "transactionId": "123e4567-e89b-12d3-a456-426614174001",
                    "type": "DEPOSIT",
                    "walletId": "123e4567-e89b-12d3-a456-426614174002",
                    "userId": "123e4567-e89b-12d3-a456-426614174003",
                    "amount": 100.00,
                    "currency": "USD",
                    "balanceAfter": 100.00,
                    "completedAt": "2024-01-01T00:00:00Z",
                    "schemaVersion": 1
                }
                """;

        // When
        TransactionCompletedEvent event = objectMapper.readValue(json, TransactionCompletedEvent.class);

        // Then
        assertEquals(1, event.schemaVersion());
        assertEquals("DEPOSIT", event.type().name());
    }

    @Test
    void transactionCompletedEvent_shouldDeserializeWithoutSchemaVersion_andUseDefault() throws Exception {
        // Given - JSON without schemaVersion (old format)
        String json = """
                {
                    "eventId": "123e4567-e89b-12d3-a456-426614174000",
                    "transactionId": "123e4567-e89b-12d3-a456-426614174001",
                    "type": "DEPOSIT",
                    "walletId": "123e4567-e89b-12d3-a456-426614174002",
                    "userId": "123e4567-e89b-12d3-a456-426614174003",
                    "amount": 100.00,
                    "currency": "USD",
                    "balanceAfter": 100.00,
                    "completedAt": "2024-01-01T00:00:00Z"
                }
                """;

        // When
        TransactionCompletedEvent event = objectMapper.readValue(json, TransactionCompletedEvent.class);

        // Then - Should get default version 1 from compact constructor
        assertEquals(1, event.schemaVersion());
    }

    @Test
    void event_shouldIgnoreUnknownFields() throws Exception {
        // Given - JSON with future unknown fields
        String json = """
                {
                    "eventId": "123e4567-e89b-12d3-a456-426614174000",
                    "transactionId": "123e4567-e89b-12d3-a456-426614174001",
                    "type": "DEPOSIT",
                    "walletId": "123e4567-e89b-12d3-a456-426614174002",
                    "userId": "123e4567-e89b-12d3-a456-426614174003",
                    "amount": 100.00,
                    "currency": "USD",
                    "balanceAfter": 100.00,
                    "completedAt": "2024-01-01T00:00:00Z",
                    "schemaVersion": 2,
                    "futureField": "should be ignored",
                    "anotherFutureField": 123
                }
                """;

        // When
        TransactionCompletedEvent event = objectMapper.readValue(json, TransactionCompletedEvent.class);

        // Then - Should deserialize without errors, ignoring unknown fields
        assertNotNull(event);
        assertEquals(2, event.schemaVersion());
    }
}
