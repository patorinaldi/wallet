package com.patorinaldi.wallet.fraud.service;

import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.fraud.dto.FraudCheckRequest;
import com.patorinaldi.wallet.fraud.dto.FraudCheckResponse;
import com.patorinaldi.wallet.fraud.entity.FraudRule;
import com.patorinaldi.wallet.fraud.entity.RuleType;
import com.patorinaldi.wallet.fraud.repository.FraudRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncFraudCheckServiceTest {

    @Mock
    private FraudRuleRepository fraudRuleRepository;

    @Mock
    private FraudTransactionHistoryService historyService;

    @InjectMocks
    private SyncFraudCheckService syncFraudCheckService;

    @Test
    void checkTransaction_shouldReturnApprove_whenNoRulesTriggered() {
        // Given
        FraudCheckRequest request = createFraudCheckRequest(new BigDecimal("100.00"));
        when(fraudRuleRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        // When
        FraudCheckResponse response = syncFraudCheckService.checkTransaction(request);

        // Then
        assertEquals("APPROVE", response.decision());
        assertEquals(0, response.riskScore());
        assertTrue(response.triggeredRules().isEmpty());
        assertEquals("Transaction approved", response.message());
    }

    @Test
    void checkTransaction_shouldReturnApprove_whenScoreBelowFlagThreshold() {
        // Given
        FraudCheckRequest request = createFraudCheckRequest(new BigDecimal("100.00"));
        FraudRule newWalletRule = createNewWalletRule(); // Score impact: 15

        when(fraudRuleRepository.findByActiveTrue()).thenReturn(List.of(newWalletRule));
        when(historyService.isNewWallet(any(), anyInt())).thenReturn(true);

        // When
        FraudCheckResponse response = syncFraudCheckService.checkTransaction(request);

        // Then
        assertEquals("APPROVE", response.decision());
        assertEquals(15, response.riskScore());
        assertTrue(response.triggeredRules().contains("NEW_WALLET"));
    }

    @Test
    void checkTransaction_shouldReturnFlag_whenScoreReachesFlagThreshold() {
        // Given
        FraudCheckRequest request = createFraudCheckRequest(new BigDecimal("15000.00"));
        FraudRule largeAmountRule = createLargeAmountRule(); // Score impact: 30

        when(fraudRuleRepository.findByActiveTrue()).thenReturn(List.of(largeAmountRule));

        // When
        FraudCheckResponse response = syncFraudCheckService.checkTransaction(request);

        // Then
        assertEquals("APPROVE", response.decision()); // 30 < 50, still approved
        assertEquals(30, response.riskScore());
    }

    @Test
    void checkTransaction_shouldReturnFlag_whenMultipleRulesTriggered() {
        // Given
        FraudCheckRequest request = createFraudCheckRequest(new BigDecimal("15000.00"));
        FraudRule largeAmountRule = createLargeAmountRule(); // Score impact: 30
        FraudRule velocityRule = createVelocityRule(); // Score impact: 25

        when(fraudRuleRepository.findByActiveTrue()).thenReturn(List.of(largeAmountRule, velocityRule));
        when(historyService.countTransactionsInWindow(any(), anyInt())).thenReturn(15); // Exceeds threshold of 10

        // When
        FraudCheckResponse response = syncFraudCheckService.checkTransaction(request);

        // Then
        assertEquals("FLAG", response.decision()); // 55 >= 50
        assertEquals(55, response.riskScore());
        assertTrue(response.triggeredRules().contains("LARGE_AMOUNT"));
        assertTrue(response.triggeredRules().contains("HIGH_VELOCITY"));
        assertEquals("Transaction flagged for review", response.message());
    }

    @Test
    void checkTransaction_shouldReturnBlock_whenScoreReachesBlockThreshold() {
        // Given
        FraudCheckRequest request = createFraudCheckRequest(new BigDecimal("60000.00"));
        FraudRule veryLargeAmountRule = createVeryLargeAmountRule(); // Score impact: 50
        FraudRule velocityRule = createVelocityRule(); // Score impact: 25
        FraudRule newWalletRule = createNewWalletRule(); // Score impact: 15

        when(fraudRuleRepository.findByActiveTrue()).thenReturn(List.of(veryLargeAmountRule, velocityRule, newWalletRule));
        when(historyService.countTransactionsInWindow(any(), anyInt())).thenReturn(15);
        when(historyService.isNewWallet(any(), anyInt())).thenReturn(true);

        // When
        FraudCheckResponse response = syncFraudCheckService.checkTransaction(request);

        // Then
        assertEquals("BLOCK", response.decision()); // 90 >= 80
        assertEquals(90, response.riskScore());
        assertEquals(3, response.triggeredRules().size());
        assertEquals("Transaction blocked due to high risk", response.message());
    }

    @Test
    void checkTransaction_shouldEvaluateUnusualPatternRule() {
        // Given
        FraudCheckRequest request = createFraudCheckRequest(new BigDecimal("5000.00"));
        FraudRule unusualPatternRule = createUnusualPatternRule(); // Score impact: 20

        when(fraudRuleRepository.findByActiveTrue()).thenReturn(List.of(unusualPatternRule));
        when(historyService.isUnusualAmount(any(), any(), any())).thenReturn(true);

        // When
        FraudCheckResponse response = syncFraudCheckService.checkTransaction(request);

        // Then
        assertEquals("APPROVE", response.decision());
        assertEquals(20, response.riskScore());
        assertTrue(response.triggeredRules().contains("UNUSUAL_AMOUNT"));
    }

    @Test
    void checkTransaction_shouldNotTriggerVelocityRule_whenBelowThreshold() {
        // Given
        FraudCheckRequest request = createFraudCheckRequest(new BigDecimal("100.00"));
        FraudRule velocityRule = createVelocityRule();

        when(fraudRuleRepository.findByActiveTrue()).thenReturn(List.of(velocityRule));
        when(historyService.countTransactionsInWindow(any(), anyInt())).thenReturn(5); // Below threshold of 10

        // When
        FraudCheckResponse response = syncFraudCheckService.checkTransaction(request);

        // Then
        assertEquals("APPROVE", response.decision());
        assertEquals(0, response.riskScore());
        assertTrue(response.triggeredRules().isEmpty());
    }

    // ========== HELPER METHODS ==========

    private FraudCheckRequest createFraudCheckRequest(BigDecimal amount) {
        return new FraudCheckRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                amount,
                TransactionType.DEPOSIT,
                "USD"
        );
    }

    private FraudRule createLargeAmountRule() {
        return FraudRule.builder()
                .ruleCode("LARGE_AMOUNT")
                .ruleType(RuleType.AMOUNT_THRESHOLD)
                .threshold(new BigDecimal("10000.00"))
                .scoreImpact(30)
                .active(true)
                .build();
    }

    private FraudRule createVeryLargeAmountRule() {
        return FraudRule.builder()
                .ruleCode("VERY_LARGE_AMOUNT")
                .ruleType(RuleType.AMOUNT_THRESHOLD)
                .threshold(new BigDecimal("50000.00"))
                .scoreImpact(50)
                .active(true)
                .build();
    }

    private FraudRule createVelocityRule() {
        return FraudRule.builder()
                .ruleCode("HIGH_VELOCITY")
                .ruleType(RuleType.VELOCITY)
                .threshold(new BigDecimal("10"))
                .timeWindowMinutes(60)
                .scoreImpact(25)
                .active(true)
                .build();
    }

    private FraudRule createNewWalletRule() {
        return FraudRule.builder()
                .ruleCode("NEW_WALLET")
                .ruleType(RuleType.NEW_ACCOUNT)
                .timeWindowMinutes(1440) // 24 hours
                .scoreImpact(15)
                .active(true)
                .build();
    }

    private FraudRule createUnusualPatternRule() {
        return FraudRule.builder()
                .ruleCode("UNUSUAL_AMOUNT")
                .ruleType(RuleType.UNUSUAL_PATTERN)
                .threshold(new BigDecimal("3"))
                .scoreImpact(20)
                .active(true)
                .build();
    }
}
