package com.patorinaldi.wallet.fraud.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.patorinaldi.wallet.common.event.FraudAlertEvent;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import com.patorinaldi.wallet.fraud.entity.FraudDecision;
import com.patorinaldi.wallet.fraud.entity.FraudRule;
import com.patorinaldi.wallet.fraud.repository.FraudRuleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.fraud.entity.FraudAnalysis;
import com.patorinaldi.wallet.fraud.repository.FraudAnalysisRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudAnalysisService {

    private final FraudAnalysisRepository fraudAnalysisRepository;
    private final FraudRuleRepository fraudRuleRepository;
    private final FraudTransactionHistoryService historyService;
    private final ApplicationEventPublisher eventPublisher;

    private static final int FLAG_THRESHOLD = 50;
    private static final int BLOCK_THRESHOLD = 80;

    @Transactional
    public void analyzeTransaction(TransactionCompletedEvent event) {

        if (fraudAnalysisRepository.existsByTransactionId(event.transactionId())) {
            log.warn("Fraud transaction analysis for transaction ID: {} already exists. Skipping.", event.transactionId());
            return;
        }

        historyService.saveTransaction(event);

        List<String> triggeredRules = new ArrayList<>();
        int riskScore = 0;

        List<FraudRule> activeRules = fraudRuleRepository.findByActiveTrue();

        for (FraudRule rule : activeRules) {
            boolean triggered = evaluateRule(rule, event);

            if (triggered) {
                riskScore += rule.getScoreImpact();
                triggeredRules.add(rule.getRuleCode());
                log.info("Rule {} triggered for transaction {}. Score impact: +{}",
                        rule.getRuleCode(), event.transactionId(), rule.getScoreImpact());
            }
        }


        FraudDecision decision;
        if (riskScore >= BLOCK_THRESHOLD) {
            decision = FraudDecision.BLOCK;
        } else if (riskScore >= FLAG_THRESHOLD) {
            decision = FraudDecision.FLAG;
        } else {
            decision = FraudDecision.APPROVE;
        }

        FraudAnalysis analysis = FraudAnalysis.builder()
                .transactionId(event.transactionId())
                .walletId(event.walletId())
                .userId(event.userId())
                .riskScore(riskScore)
                .triggeredRules(triggeredRules)
                .decision(decision)
                .amount(event.amount())
                .transactionType(event.type())
                .analyzedAt(java.time.Instant.now())
                .build();

        FraudAnalysis savedAnalysis = fraudAnalysisRepository.save(analysis);

        if (decision == FraudDecision.BLOCK) {
            UserBlockedEvent userBlockedEvent = new UserBlockedEvent(
                    savedAnalysis.getUserId(),
                    savedAnalysis.getTransactionId(),
                    "Fraudulent activity detected: " + String.join(", ", savedAnalysis.getTriggeredRules()),
                    savedAnalysis.getRiskScore(),
                    Instant.now()
            );
            eventPublisher.publishEvent(userBlockedEvent);
        }

        if (decision != FraudDecision.APPROVE) {
            FraudAlertEvent fraudAlertEvent = new FraudAlertEvent(
                    savedAnalysis.getId(),
                    savedAnalysis.getTransactionId(),
                    savedAnalysis.getRiskScore(),
                    decision.name()
            );
            eventPublisher.publishEvent(fraudAlertEvent);
        }

        log.info("Fraud analysis for transaction ID: {} saved with decision: {} and risk score: {}",
                event.transactionId(), decision, riskScore);

    }

    private boolean evaluateRule(FraudRule rule, TransactionCompletedEvent event) {
        return switch (rule.getRuleType()) {
            case AMOUNT_THRESHOLD -> evaluateAmountThreshold(rule, event);
            case VELOCITY -> evaluateVelocity(rule, event);
            case NEW_ACCOUNT -> evaluateNewAccount(rule, event);
            case UNUSUAL_PATTERN -> evaluateUnusualPattern(rule, event);
        };
    }

    private boolean evaluateUnusualPattern(FraudRule rule, TransactionCompletedEvent event) {
        return historyService.isUnusualAmount(
                event.walletId(),
                event.amount(),
                rule.getThreshold()
        );
    }

    private boolean evaluateNewAccount(FraudRule rule, TransactionCompletedEvent event) {
        return historyService.isNewWallet(
                event.walletId(),
                rule.getTimeWindowMinutes());
    }

    private boolean evaluateVelocity(FraudRule rule, TransactionCompletedEvent event) {
        Integer count = historyService.countTransactionsInWindow(
                event.walletId(),
                rule.getTimeWindowMinutes()
        );
        return count > rule.getThreshold().intValue();
    }

    private boolean evaluateAmountThreshold(FraudRule rule, TransactionCompletedEvent event) {
        return event.amount().compareTo(rule.getThreshold()) > 0;
    }

}
