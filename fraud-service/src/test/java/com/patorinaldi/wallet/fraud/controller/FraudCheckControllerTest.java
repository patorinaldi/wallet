package com.patorinaldi.wallet.fraud.controller;

import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.fraud.dto.FraudCheckRequest;
import com.patorinaldi.wallet.fraud.dto.FraudCheckResponse;
import com.patorinaldi.wallet.fraud.service.SyncFraudCheckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudCheckControllerTest {

    @Mock
    private SyncFraudCheckService syncFraudCheckService;

    @InjectMocks
    private FraudCheckController fraudCheckController;

    @Test
    void checkTransaction_shouldReturnApprove_whenTransactionIsLowRisk() {
        // Given
        FraudCheckRequest request = createRequest(new BigDecimal("100.00"));
        FraudCheckResponse response = FraudCheckResponse.approve(0, Collections.emptyList());

        when(syncFraudCheckService.checkTransaction(any())).thenReturn(response);

        // When
        ResponseEntity<FraudCheckResponse> result = fraudCheckController.checkTransaction(request);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("APPROVE", result.getBody().decision());
        assertEquals(0, result.getBody().riskScore());
        assertEquals("Transaction approved", result.getBody().message());
    }

    @Test
    void checkTransaction_shouldReturnFlag_whenTransactionIsMediumRisk() {
        // Given
        FraudCheckRequest request = createRequest(new BigDecimal("15000.00"));
        FraudCheckResponse response = FraudCheckResponse.flag(55, List.of("LARGE_AMOUNT", "HIGH_VELOCITY"));

        when(syncFraudCheckService.checkTransaction(any())).thenReturn(response);

        // When
        ResponseEntity<FraudCheckResponse> result = fraudCheckController.checkTransaction(request);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("FLAG", result.getBody().decision());
        assertEquals(55, result.getBody().riskScore());
        assertEquals(2, result.getBody().triggeredRules().size());
        assertTrue(result.getBody().triggeredRules().contains("LARGE_AMOUNT"));
    }

    @Test
    void checkTransaction_shouldReturnBlock_whenTransactionIsHighRisk() {
        // Given
        FraudCheckRequest request = createRequest(new BigDecimal("60000.00"));
        FraudCheckResponse response = FraudCheckResponse.block(85, List.of("VERY_LARGE_AMOUNT", "HIGH_VELOCITY"));

        when(syncFraudCheckService.checkTransaction(any())).thenReturn(response);

        // When
        ResponseEntity<FraudCheckResponse> result = fraudCheckController.checkTransaction(request);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("BLOCK", result.getBody().decision());
        assertEquals(85, result.getBody().riskScore());
        assertEquals("Transaction blocked due to high risk", result.getBody().message());
    }

    private FraudCheckRequest createRequest(BigDecimal amount) {
        return new FraudCheckRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                amount,
                TransactionType.DEPOSIT,
                "USD"
        );
    }
}
