package com.patorinaldi.wallet.fraud.service;

import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.FraudAlertEvent;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import com.patorinaldi.wallet.fraud.entity.FraudAnalysis;
import com.patorinaldi.wallet.fraud.entity.FraudDecision;
import com.patorinaldi.wallet.fraud.entity.FraudRule;
import com.patorinaldi.wallet.fraud.entity.RuleType;
import com.patorinaldi.wallet.fraud.repository.FraudAnalysisRepository;
import com.patorinaldi.wallet.fraud.repository.FraudRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudAnalysisServiceTest {

    @Mock
    private FraudAnalysisRepository fraudAnalysisRepository;

    @Mock
    private FraudRuleRepository fraudRuleRepository;

    @Mock
    private FraudTransactionHistoryService historyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FraudAnalysisService fraudAnalysisService;

    @Captor
    private ArgumentCaptor<FraudAnalysis> analysisCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    @Test
    void analyzeTransaction_shouldSkip_whenAnalysisAlreadyExists() {
        // Given
        TransactionCompletedEvent event = createDummyEvent();
        when(fraudAnalysisRepository.existsByTransactionId(event.transactionId())).thenReturn(true);

        // When
        fraudAnalysisService.analyzeTransaction(event);

        // Then
        verify(fraudAnalysisRepository).existsByTransactionId(event.transactionId());
        verify(fraudAnalysisRepository, never()).save(any());
        verify(fraudRuleRepository, never()).findByActiveTrue();
        verify(historyService, never()).saveTransaction(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void analyzeTransaction_shouldDecideApprove_whenNoRulesAreTriggered() {
        // Given
        TransactionCompletedEvent event = createDummyEvent();
        when(fraudAnalysisRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(fraudRuleRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        when(fraudAnalysisRepository.save(any(FraudAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        fraudAnalysisService.analyzeTransaction(event);

        // Then
        verify(fraudAnalysisRepository).save(analysisCaptor.capture());
        FraudAnalysis capturedAnalysis = analysisCaptor.getValue();
        assertEquals(FraudDecision.APPROVE, capturedAnalysis.getDecision());
        assertEquals(0, capturedAnalysis.getRiskScore());
        assertTrue(capturedAnalysis.getTriggeredRules().isEmpty());

        assertEquals(event.transactionId(), capturedAnalysis.getTransactionId());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void analyzeTransaction_shouldDecideFlagAndPublishAlert_whenScoreReachesFlagThreshold() {
        // Given
        TransactionCompletedEvent event = createHighAmountEvent(BigDecimal.valueOf(7000));
        FraudRule rule = createLargeAmountRule();

        when(fraudAnalysisRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(fraudAnalysisRepository.save(any(FraudAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fraudRuleRepository.findByActiveTrue()).thenReturn(Collections.singletonList(rule));

        // When
        fraudAnalysisService.analyzeTransaction(event);

        // Then
        verify(fraudAnalysisRepository).save(analysisCaptor.capture());
        FraudAnalysis capturedAnalysis = analysisCaptor.getValue();
        assertEquals(FraudDecision.FLAG, capturedAnalysis.getDecision());
        assertEquals(60, capturedAnalysis.getRiskScore());
        assertTrue(capturedAnalysis.getTriggeredRules().contains("LARGE_TRANSACTION"));

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        Object capturedEvent = eventCaptor.getValue();
        assertInstanceOf(FraudAlertEvent.class, capturedEvent);

        FraudAlertEvent alert = (FraudAlertEvent) capturedEvent;
        assertEquals(60, alert.riskScore());
        assertEquals("FLAG", alert.decision());
    }

    @Test
    void analyzeTransaction_shouldDecideBlockAndPublishEvents_whenScoreReachesBlockThreshold() {
        // Given
        TransactionCompletedEvent event = createHighAmountEvent(BigDecimal.valueOf(12000));
        FraudRule largeAmountRule = createLargeAmountRule();
        FraudRule highVelocityRule = createHighVelocityRule();

        when(fraudAnalysisRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(fraudAnalysisRepository.save(any(FraudAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fraudRuleRepository.findByActiveTrue()).thenReturn(List.of(largeAmountRule, highVelocityRule));
        when(historyService.countTransactionsInWindow(any(), anyInt())).thenReturn(15);

        // When
        fraudAnalysisService.analyzeTransaction(event);

        // Then
        verify(fraudAnalysisRepository).save(analysisCaptor.capture());
        FraudAnalysis capturedAnalysis = analysisCaptor.getValue();
        assertEquals(FraudDecision.BLOCK, capturedAnalysis.getDecision());
        assertEquals(85, capturedAnalysis.getRiskScore());
        assertEquals(2, capturedAnalysis.getTriggeredRules().size());

        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        List<Object> capturedEvents = eventCaptor.getAllValues();
        assertEquals(2, capturedEvents.size());

        UserBlockedEvent blockedEvent = capturedEvents.stream()
                .filter(UserBlockedEvent.class::isInstance)
                .map(UserBlockedEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("UserBlockedEvent not found"));

        assertEquals(event.userId(), blockedEvent.userId());
        assertEquals(85, blockedEvent.riskScore());

        FraudAlertEvent alertEvent = capturedEvents.stream()
                .filter(FraudAlertEvent.class::isInstance)
                .map(FraudAlertEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("FraudAlertEvent not found"));

        assertEquals(85, alertEvent.riskScore());
        assertEquals("BLOCK", alertEvent.decision());

    }

    private TransactionCompletedEvent createDummyEvent() {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.TRANSFER_IN)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("175.00"))
                .relatedWalletId(UUID.randomUUID())
                .completedAt(Instant.now())
                .build();
    }

    private TransactionCompletedEvent createHighAmountEvent(BigDecimal amount) {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(amount) // Use the provided amount
                .currency("USD")
                .balanceAfter(amount)
                .completedAt(Instant.now())
                .build();
    }

    private FraudRule createLargeAmountRule() {
        // Return a new FraudRule with type AMOUNT_THRESHOLD, the given score, and threshold
        return FraudRule.builder()
                .ruleCode("LARGE_TRANSACTION")
                .description("Transaction amount exceeds the single transaction limit.")
                .ruleType(RuleType.AMOUNT_THRESHOLD) // Assuming you have a RuleType enum
                .scoreImpact(60)
                .threshold(new BigDecimal("5000.00"))
                .active(true)
                .build();
    }

    private FraudRule createHighVelocityRule() {
        return FraudRule.builder()
                .ruleCode("HIGH_VELOCITY")
                .ruleType(RuleType.VELOCITY)
                .scoreImpact(25)
                .threshold(new BigDecimal("10")) // Threshold is 10 transactions
                .timeWindowMinutes(60) // in the last 60 minutes
                .active(true)
                .build();
    }
}
