package com.patorinaldi.wallet.transaction.client;

import com.patorinaldi.wallet.common.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraudClientImplTest {

    @Test
    void fraudCheckResponse_shouldCorrectlyIdentifyApproved() {
        // Given
        FraudCheckResponse response = new FraudCheckResponse(
                0, "APPROVE", List.of(), "Transaction approved"
        );

        // Then
        assertTrue(response.isApproved());
        assertFalse(response.isBlocked());
        assertFalse(response.isFlagged());
    }

    @Test
    void fraudCheckResponse_shouldCorrectlyIdentifyBlocked() {
        // Given
        FraudCheckResponse response = new FraudCheckResponse(
                85, "BLOCK", List.of("VERY_LARGE_AMOUNT", "HIGH_VELOCITY"), "Transaction blocked due to high risk"
        );

        // Then
        assertTrue(response.isBlocked());
        assertFalse(response.isApproved());
        assertFalse(response.isFlagged());
    }

    @Test
    void fraudCheckResponse_shouldCorrectlyIdentifyFlagged() {
        // Given
        FraudCheckResponse response = new FraudCheckResponse(
                55, "FLAG", List.of("LARGE_AMOUNT"), "Transaction flagged for review"
        );

        // Then
        assertTrue(response.isFlagged());
        assertFalse(response.isApproved());
        assertFalse(response.isBlocked());
    }

    @Test
    void fraudCheckRequest_shouldContainCorrectFields() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500.00");

        // When
        FraudCheckRequest request = new FraudCheckRequest(
                walletId, userId, amount, TransactionType.WITHDRAWAL, "EUR"
        );

        // Then
        assertEquals(walletId, request.walletId());
        assertEquals(userId, request.userId());
        assertEquals(amount, request.amount());
        assertEquals(TransactionType.WITHDRAWAL, request.transactionType());
        assertEquals("EUR", request.currency());
    }

    @Test
    void fallbackResponse_shouldReturnFlagWithServiceUnavailable() {
        // This tests the fallback response structure
        FraudCheckResponse fallbackResponse = new FraudCheckResponse(
                0,
                "FLAG",
                List.of("FRAUD_SERVICE_UNAVAILABLE"),
                "Fraud service unavailable - transaction flagged for manual review"
        );

        // Then
        assertTrue(fallbackResponse.isFlagged());
        assertFalse(fallbackResponse.isBlocked());
        assertEquals(0, fallbackResponse.riskScore());
        assertTrue(fallbackResponse.triggeredRules().contains("FRAUD_SERVICE_UNAVAILABLE"));
    }
}
