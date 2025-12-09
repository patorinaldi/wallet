package com.patorinaldi.wallet.fraud;

import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.FraudAlertEvent;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import com.patorinaldi.wallet.fraud.entity.FraudAnalysis;
import com.patorinaldi.wallet.fraud.entity.FraudTransactionHistory;
import com.patorinaldi.wallet.fraud.entity.FraudDecision;
import com.patorinaldi.wallet.fraud.entity.FraudRule;
import com.patorinaldi.wallet.fraud.entity.RuleType;
import com.patorinaldi.wallet.fraud.repository.FraudAnalysisRepository;
import com.patorinaldi.wallet.fraud.repository.FraudRuleRepository;
import com.patorinaldi.wallet.fraud.repository.FraudTransactionHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FraudAnalysisServiceIntegrationTest {

    // Score thresholds (must match FraudAnalysisService constants)
    private static final int FLAG_THRESHOLD = 50;
    private static final int BLOCK_THRESHOLD = 80;

    // Test rule score impacts
    private static final int AMOUNT_THRESHOLD_SCORE = 60;
    private static final int VELOCITY_SCORE = 25;
    private static final int NEW_ACCOUNT_SCORE = 30;
    private static final int UNUSUAL_PATTERN_SCORE = 40;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private FraudRuleRepository fraudRuleRepository;

    @Autowired
    private FraudAnalysisRepository fraudAnalysisRepository;

    @Autowired
    private FraudTransactionHistoryRepository fraudTransactionHistoryRepository;

    @Autowired
    private TestKafkaConsumer testKafkaConsumer;

    @BeforeEach
    void setup() {
        fraudAnalysisRepository.deleteAll();
        fraudRuleRepository.deleteAll();
        fraudTransactionHistoryRepository.deleteAll();
        testKafkaConsumer.clear();
    }

    @Component
    public static class TestKafkaConsumer {
        private final List<FraudAlertEvent> receivedAlerts = new CopyOnWriteArrayList<>();
        private final List<UserBlockedEvent> receivedBlocks = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = "fraud-alert", groupId = "test-fraud-consumer")
        public void receiveFraudAlert(FraudAlertEvent event) {
            receivedAlerts.add(event);
        }

        @KafkaListener(topics = "user-blocked", groupId = "test-fraud-consumer")
        public void receiveUserBlocked(UserBlockedEvent event) {
            receivedBlocks.add(event);
        }

        public List<FraudAlertEvent> getReceivedAlerts() {
            return receivedAlerts;
        }

        public List<UserBlockedEvent> getReceivedBlocks() {
            return receivedBlocks;
        }

        public void clear() {
            receivedAlerts.clear();
            receivedBlocks.clear();
        }
    }

    @Test
    void shouldSaveApproveDecisionAndNotPublishEvents() {
        // Given - no fraud rules in the database
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("100.00"));

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<FraudAnalysis> analyses = fraudAnalysisRepository.findAll();
            assertFalse(analyses.isEmpty());
            assertEquals(1, analyses.size());

            FraudAnalysis analysis = analyses.get(0);
            assertEquals(FraudDecision.APPROVE, analysis.getDecision());
            assertEquals(0, analysis.getRiskScore());
        });

        // Verify no events were published
        assertTrue(testKafkaConsumer.getReceivedAlerts().isEmpty());
        assertTrue(testKafkaConsumer.getReceivedBlocks().isEmpty());
    }

    @Test
    void shouldSaveFlagDecisionAndPublishAlertEvent() {
        // Given - AMOUNT_THRESHOLD_SCORE (60) >= FLAG_THRESHOLD (50) but < BLOCK_THRESHOLD (80)
        fraudRuleRepository.save(createRule(RuleType.AMOUNT_THRESHOLD, AMOUNT_THRESHOLD_SCORE, new BigDecimal("500"), 60));
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("600.00"));

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Verify database state
            FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(FraudDecision.FLAG, analysis.getDecision());
            assertEquals(AMOUNT_THRESHOLD_SCORE, analysis.getRiskScore());

            // Verify Kafka events - FLAG should publish alert but not block
            assertEquals(1, testKafkaConsumer.getReceivedAlerts().size());
            assertTrue(testKafkaConsumer.getReceivedBlocks().isEmpty());

            FraudAlertEvent alert = testKafkaConsumer.getReceivedAlerts().get(0);
            assertEquals(analysis.getId(), alert.analysisId());
            assertEquals(AMOUNT_THRESHOLD_SCORE, alert.riskScore());
            assertEquals("FLAG", alert.decision());
        });
    }

    @Test
    void shouldSaveBlockDecisionAndPublishBothEvents() {
        // Given - Combined score: AMOUNT_THRESHOLD_SCORE (60) + VELOCITY_SCORE (25) = 85 >= BLOCK_THRESHOLD (80)
        int expectedCombinedScore = AMOUNT_THRESHOLD_SCORE + VELOCITY_SCORE; // 85
        fraudRuleRepository.save(createRule(RuleType.AMOUNT_THRESHOLD, AMOUNT_THRESHOLD_SCORE, new BigDecimal("1000"), 60));
        fraudRuleRepository.save(createRule(RuleType.VELOCITY, VELOCITY_SCORE, new BigDecimal("5"), 60));

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        // Create transaction history to trigger velocity rule (>5 transactions in window)
        // Note: The service saves the incoming transaction BEFORE evaluating rules,
        // so we need 5 existing transactions + 1 new = 6 total, which exceeds threshold of 5
        for (int i = 0; i < 5; i++) {
            FraudTransactionHistory history = FraudTransactionHistory.builder()
                    .transactionId(UUID.randomUUID())
                    .walletId(walletId)
                    .userId(userId)
                    .amount(new BigDecimal("100.00"))
                    .transactionType(TransactionType.DEPOSIT)
                    .currency("USD")
                    .occurredAt(Instant.now().minusSeconds(i * 60))
                    .build();
            fraudTransactionHistoryRepository.save(history);
        }

        // Create event with same walletId to trigger velocity check
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("1200.00"), userId, walletId);

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Verify database state
            FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(FraudDecision.BLOCK, analysis.getDecision());
            assertEquals(expectedCombinedScore, analysis.getRiskScore());

            // Verify Kafka events - BLOCK should publish both alert and block events
            assertEquals(1, testKafkaConsumer.getReceivedAlerts().size());
            assertEquals(1, testKafkaConsumer.getReceivedBlocks().size());

            // Check UserBlockedEvent
            UserBlockedEvent blockedEvent = testKafkaConsumer.getReceivedBlocks().get(0);
            assertEquals(event.userId(), blockedEvent.userId());
            assertEquals(expectedCombinedScore, blockedEvent.riskScore());

            // Check FraudAlertEvent
            FraudAlertEvent alertEvent = testKafkaConsumer.getReceivedAlerts().get(0);
            assertEquals(analysis.getId(), alertEvent.analysisId());
            assertEquals(expectedCombinedScore, alertEvent.riskScore());
            assertEquals("BLOCK", alertEvent.decision());
        });
    }

    @Test
    void shouldSkipAnalysis_whenTransactionAlreadyAnalyzed() {
        // Given - First, process a transaction successfully
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("100.00"));
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Wait for first analysis to complete
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(fraudAnalysisRepository.findByTransactionId(event.transactionId()).isPresent());
        });

        FraudAnalysis firstAnalysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
        long initialCount = fraudAnalysisRepository.count();

        // When - Send the same transaction again (duplicate event)
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then - Wait a bit and verify no duplicate was created
        await().pollDelay(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(initialCount, fraudAnalysisRepository.count());
            // Verify the original analysis is unchanged
            FraudAnalysis unchangedAnalysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(firstAnalysis.getId(), unchangedAnalysis.getId());
            assertEquals(firstAnalysis.getDecision(), unchangedAnalysis.getDecision());
        });
    }

    @Test
    void shouldTriggerNewAccountRule_whenWalletHasNoHistory() {
        // Given - NEW_ACCOUNT rule that triggers for wallets with no transaction history
        fraudRuleRepository.save(createRule(RuleType.NEW_ACCOUNT, NEW_ACCOUNT_SCORE, null, 1440)); // 24 hours window

        // Create a brand new wallet with no history
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("500.00"), userId, walletId);

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then - NEW_ACCOUNT_SCORE (30) < FLAG_THRESHOLD (50), so decision should be APPROVE
        // but with a non-zero risk score
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(NEW_ACCOUNT_SCORE, analysis.getRiskScore());
            assertEquals(FraudDecision.APPROVE, analysis.getDecision());
        });
    }

    @Test
    void shouldTriggerUnusualPatternRule_whenAmountExceedsAverageByMultiplier() {
        // Given - UNUSUAL_PATTERN rule with multiplier of 2x average
        // Note: The service calculates average from history AFTER saving the new transaction,
        // so we need to ensure the new transaction significantly exceeds the updated average.
        BigDecimal multiplier = new BigDecimal("2");
        fraudRuleRepository.save(createRule(RuleType.UNUSUAL_PATTERN, UNUSUAL_PATTERN_SCORE, multiplier, 60));

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        // Create transaction history with average amount of $100 (10 transactions)
        for (int i = 0; i < 10; i++) {
            FraudTransactionHistory history = FraudTransactionHistory.builder()
                    .transactionId(UUID.randomUUID())
                    .walletId(walletId)
                    .userId(userId)
                    .amount(new BigDecimal("100.00"))
                    .transactionType(TransactionType.DEPOSIT)
                    .currency("USD")
                    .occurredAt(Instant.now().minusSeconds(i * 60))
                    .build();
            fraudTransactionHistoryRepository.save(history);
        }

        // After new transaction is saved, average will be recalculated:
        // With 10 x $100 = $1000 existing, if we add $5000 new transaction:
        // New average = ($1000 + $5000) / 11 = ~$545
        // Threshold = $545 * 2 = ~$1091
        // Transaction amount $5000 > $1091, so rule should trigger
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("5000.00"), userId, walletId);

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then - UNUSUAL_PATTERN_SCORE (40) < FLAG_THRESHOLD (50), so decision should be APPROVE
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            // First verify the analysis was created
            assertTrue(fraudAnalysisRepository.findByTransactionId(event.transactionId()).isPresent(),
                    "Analysis should exist for transaction " + event.transactionId());

            FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(UNUSUAL_PATTERN_SCORE, analysis.getRiskScore());
            assertEquals(FraudDecision.APPROVE, analysis.getDecision());
        });
    }

    @Test
    void shouldApprove_whenScoreIsExactlyAtFlagThresholdMinusOne() {
        // Given - Score of 49 (just below FLAG_THRESHOLD of 50)
        int belowFlagScore = FLAG_THRESHOLD - 1; // 49
        fraudRuleRepository.save(createRule(RuleType.AMOUNT_THRESHOLD, belowFlagScore, new BigDecimal("100"), 60));
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("150.00"));

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(belowFlagScore, analysis.getRiskScore());
            assertEquals(FraudDecision.APPROVE, analysis.getDecision());

            // No events should be published for APPROVE
            assertTrue(testKafkaConsumer.getReceivedAlerts().isEmpty());
            assertTrue(testKafkaConsumer.getReceivedBlocks().isEmpty());
        });
    }

    @Test
    void shouldFlag_whenScoreIsExactlyAtFlagThreshold() {
        // Given - Score of exactly 50 (FLAG_THRESHOLD)
        fraudRuleRepository.save(createRule(RuleType.AMOUNT_THRESHOLD, FLAG_THRESHOLD, new BigDecimal("100"), 60));
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("150.00"));

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(FLAG_THRESHOLD, analysis.getRiskScore());
            assertEquals(FraudDecision.FLAG, analysis.getDecision());

            // FLAG should publish alert but not block
            assertEquals(1, testKafkaConsumer.getReceivedAlerts().size());
            assertTrue(testKafkaConsumer.getReceivedBlocks().isEmpty());
        });
    }

    @Test
    void shouldFlag_whenScoreIsExactlyAtBlockThresholdMinusOne() {
        // Given - Score of 79 (just below BLOCK_THRESHOLD of 80)
        int belowBlockScore = BLOCK_THRESHOLD - 1; // 79
        fraudRuleRepository.save(createRule(RuleType.AMOUNT_THRESHOLD, belowBlockScore, new BigDecimal("100"), 60));
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("150.00"));

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(belowBlockScore, analysis.getRiskScore());
            assertEquals(FraudDecision.FLAG, analysis.getDecision());

            // FLAG should publish alert but not block
            assertEquals(1, testKafkaConsumer.getReceivedAlerts().size());
            assertTrue(testKafkaConsumer.getReceivedBlocks().isEmpty());
        });
    }

    @Test
    void shouldBlock_whenScoreIsExactlyAtBlockThreshold() {
        // Given - Score of exactly 80 (BLOCK_THRESHOLD)
        fraudRuleRepository.save(createRule(RuleType.AMOUNT_THRESHOLD, BLOCK_THRESHOLD, new BigDecimal("100"), 60));
        TransactionCompletedEvent event = createTransactionEvent(new BigDecimal("150.00"));

        // When
        kafkaTemplate.send("transaction-completed", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(event.transactionId()).orElseThrow();
            assertEquals(BLOCK_THRESHOLD, analysis.getRiskScore());
            assertEquals(FraudDecision.BLOCK, analysis.getDecision());

            // BLOCK should publish both events
            assertEquals(1, testKafkaConsumer.getReceivedAlerts().size());
            assertEquals(1, testKafkaConsumer.getReceivedBlocks().size());
        });
    }

    // ========== HELPER METHODS ==========

    private TransactionCompletedEvent createTransactionEvent(BigDecimal amount) {
        return createTransactionEvent(amount, UUID.randomUUID(), UUID.randomUUID());
    }

    private TransactionCompletedEvent createTransactionEvent(BigDecimal amount, UUID userId, UUID walletId) {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .userId(userId)
                .walletId(walletId)
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .currency("USD")
                .balanceAfter(amount)
                .completedAt(Instant.now())
                .build();
    }

    private FraudRule createRule(RuleType type, int score, BigDecimal threshold, int timeWindowMinutes) {
        return FraudRule.builder()
                .ruleCode(type.name() + "_" + score)
                .description("Test rule for " + type.name())
                .ruleType(type)
                .scoreImpact(score)
                .threshold(threshold)
                .timeWindowMinutes(timeWindowMinutes)
                .active(true)
                .createdAt(Instant.now())
                .build();
    }
}
