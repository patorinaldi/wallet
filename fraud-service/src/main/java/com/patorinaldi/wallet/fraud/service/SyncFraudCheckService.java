package com.patorinaldi.wallet.fraud.service;

import com.patorinaldi.wallet.fraud.dto.FraudCheckRequest;
import com.patorinaldi.wallet.fraud.dto.FraudCheckResponse;
import com.patorinaldi.wallet.fraud.entity.FraudDecision;
import com.patorinaldi.wallet.fraud.entity.FraudRule;
import com.patorinaldi.wallet.fraud.repository.FraudRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncFraudCheckService {

    private final FraudRuleRepository fraudRuleRepository;
    private final FraudTransactionHistoryService historyService;

    private static final int FLAG_THRESHOLD = 50;
    private static final int BLOCK_THRESHOLD = 80;

    @Transactional(readOnly = true)
    public FraudCheckResponse checkTransaction(FraudCheckRequest request) {
        log.info("Performing sync fraud check for wallet: {}, amount: {}, type: {}",
                request.walletId(), request.amount(), request.transactionType());

        List<String> triggeredRules = new ArrayList<>();
        int riskScore = 0;

        List<FraudRule> activeRules = fraudRuleRepository.findByActiveTrue();

        for (FraudRule rule : activeRules) {
            boolean triggered = evaluateRule(rule, request);

            if (triggered) {
                riskScore += rule.getScoreImpact();
                triggeredRules.add(rule.getRuleCode());
                log.info("Rule {} triggered for sync check (wallet: {}). Score impact: +{}",
                        rule.getRuleCode(), request.walletId(), rule.getScoreImpact());
            }
        }

        FraudDecision decision = determineDecision(riskScore);

        log.info("Sync fraud check completed for wallet: {}. Decision: {}, Risk Score: {}, Triggered Rules: {}",
                request.walletId(), decision, riskScore, triggeredRules);

        return buildResponse(decision, riskScore, triggeredRules);
    }

    private FraudDecision determineDecision(int riskScore) {
        if (riskScore >= BLOCK_THRESHOLD) {
            return FraudDecision.BLOCK;
        } else if (riskScore >= FLAG_THRESHOLD) {
            return FraudDecision.FLAG;
        } else {
            return FraudDecision.APPROVE;
        }
    }

    private FraudCheckResponse buildResponse(FraudDecision decision, int riskScore, List<String> triggeredRules) {
        return switch (decision) {
            case APPROVE -> FraudCheckResponse.approve(riskScore, triggeredRules);
            case FLAG -> FraudCheckResponse.flag(riskScore, triggeredRules);
            case BLOCK -> FraudCheckResponse.block(riskScore, triggeredRules);
        };
    }

    private boolean evaluateRule(FraudRule rule, FraudCheckRequest request) {
        return switch (rule.getRuleType()) {
            case AMOUNT_THRESHOLD -> evaluateAmountThreshold(rule, request);
            case VELOCITY -> evaluateVelocity(rule, request);
            case NEW_ACCOUNT -> evaluateNewAccount(rule, request);
            case UNUSUAL_PATTERN -> evaluateUnusualPattern(rule, request);
        };
    }

    private boolean evaluateAmountThreshold(FraudRule rule, FraudCheckRequest request) {
        return request.amount().compareTo(rule.getThreshold()) > 0;
    }

    private boolean evaluateVelocity(FraudRule rule, FraudCheckRequest request) {
        Integer count = historyService.countTransactionsInWindow(
                request.walletId(),
                rule.getTimeWindowMinutes()
        );
        return count > rule.getThreshold().intValue();
    }

    private boolean evaluateNewAccount(FraudRule rule, FraudCheckRequest request) {
        return historyService.isNewWallet(
                request.walletId(),
                rule.getTimeWindowMinutes()
        );
    }

    private boolean evaluateUnusualPattern(FraudRule rule, FraudCheckRequest request) {
        return historyService.isUnusualAmount(
                request.walletId(),
                request.amount(),
                rule.getThreshold()
        );
    }
}
