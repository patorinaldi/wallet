_# Wallet Microservices - Revised Implementation Plan

> **Version:** 3.0
> **Last Updated:** 2025-12-07
> **Status:** Phase 0 & 1 Complete - Ready for Phase 2 (Fraud Service)

---

## Executive Summary

This revised plan addresses critical architectural concerns raised during review, particularly around **idempotency**, **event ordering**, **dead letter queues**, and **observability**. The implementation order has been restructured to front-load cross-cutting concerns that become exponentially harder to retrofit.

---

## Current Implementation Status

### ✅ Phase 0: Cross-Cutting Infrastructure (COMPLETE)
- **Distributed Tracing:** Zipkin + Micrometer configured across all services
- **Dead Letter Queues:** KafkaErrorConfig in common module with exponential backoff
- **Idempotency Patterns:** Interface defined, implemented in transaction-service and ledger-service

### ✅ Phase 1: Core Services (COMPLETE)

**Account Service (Port 8081):**
- User registration and management (CRUD + soft delete)
- Multi-currency wallet creation and management
- Event publishing: `user-registered`, `wallet-created`
- Validation: email uniqueness, phone number format, currency codes
- Comprehensive unit and integration tests

**Transaction Service (Port 8082):**
- Deposit, withdrawal, and P2P transfer operations
- Balance management with optimistic locking (`@Version`)
- Idempotency via unique `idempotencyKey` field
- Event consumption: `wallet-created` → initializes WalletBalance
- Event publishing: `transaction-completed`, `transaction-failed`
- Query endpoints: transaction history (paginated), balance lookup
- Comprehensive unit and integration tests

**Ledger Service (Port 8083):**
- Double-entry bookkeeping (every transaction = 2 entries)
- Account types: USER_WALLET, SYSTEM_BANK, SYSTEM_FEES, SYSTEM_SUSPENSE
- System account seeding on startup (USD)
- Event consumption: `transaction-completed` → records ledger entries
- Idempotency via transactionId uniqueness in LedgerJournal
- Comprehensive unit and integration tests

**Common Module:**
- Shared event definitions (UserRegisteredEvent, WalletCreatedEvent, TransactionCompletedEvent, TransactionFailedEvent)
- Shared enums (TransactionType, TransactionStatus)
- Idempotency framework (IdempotencyChecker, ProcessedEvent, ProcessingOutcome)
- Kafka error handling configuration (KafkaErrorConfig)

### 🔲 Phase 2: Fraud Analysis Service (NOT STARTED)
- Stub application exists, no business logic implemented

### 🔲 Phase 3: Notification Service (NOT STARTED)
- Stub application exists, no business logic implemented

### 🔲 Phase 4-5: Production Hardening (NOT STARTED)
- API Gateway, sync fraud checks, schema versioning, secret management

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Gateway (Future)                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
           ┌───────────────────────────┼───────────────────────────┐
           │                           │                           │
           ▼                           ▼                           ▼
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  Account Svc    │         │ Transaction Svc │         │   Fraud Svc     │
│    (8081)       │         │     (8082)      │◄────────│    (8085)       │
│                 │         │                 │ sync    │  Analysis Only  │
│  - Users        │         │  - Deposits     │ check*  │                 │
│  - Wallets      │         │  - Withdrawals  │         │  - Risk Scoring │
└────────┬────────┘         │  - Transfers    │         │  - Anomalies    │
         │                  └────────┬────────┘         └────────┬────────┘
         │                           │                           │
         │ user-registered           │ transaction-completed     │ fraud-alert
         │ wallet-created            │ transaction-failed        │
         ▼                           ▼                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                          Apache Kafka + Schema Registry                     │
│                                                                             │
│  Topics:                                                                    │
│  ├── user-registered                                                        │
│  ├── wallet-created                                                         │
│  ├── transaction-completed                                                  │
│  ├── transaction-failed                                                     │
│  ├── fraud-alert                                                            │
│  ├── notification-requested                                                 │
│  └── *.DLT (Dead Letter Topics)                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
         │                           │                           │
         ▼                           ▼                           ▼
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│ Notification    │         │  Ledger Svc     │         │   Zipkin        │
│     Svc         │         │    (8083)       │         │   (9411)        │
│    (8084)       │         │                 │         │                 │
│                 │         │  Double-Entry   │         │  Distributed    │
│  - Email        │         │  Bookkeeping    │         │  Tracing        │
│  - SMS (future) │         │                 │         │                 │
└─────────────────┘         └─────────────────┘         └─────────────────┘

* Sync fraud check is Phase 5 (Production Hardening)
```

---

## Phase 0: Cross-Cutting Infrastructure (Do First)

**Rationale:** These components become exponentially harder to add later. Adding tracing to 6 services after the fact requires retrofitting context propagation everywhere.

### 0.1 Distributed Tracing Setup

**Why Now:** Debugging microservices without trace correlation is a nightmare. A single user request can span 5+ services.

**Implementation:**

1. Add dependencies to all services:
```gradle
// In each service's build.gradle
implementation 'io.micrometer:micrometer-tracing-bridge-brave'
implementation 'io.zipkin.reporter2:zipkin-reporter-brave'
```

2. Add Zipkin to docker-compose:
```yaml
zipkin:
  image: openzipkin/zipkin:latest
  ports:
    - "9411:9411"
  networks:
    - wallet-network
```

3. Configure each service:
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, reduce in prod
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

4. Kafka trace propagation (auto with Spring):
```yaml
spring:
  kafka:
    producer:
      properties:
        spring.json.add.type.headers: false
    consumer:
      properties:
        spring.json.type.mapping: >
          user-registered:com.wallet.common.event.UserRegisteredEvent,
          wallet-created:com.wallet.common.event.WalletCreatedEvent,
          transaction-completed:com.wallet.common.event.TransactionCompletedEvent,
          transaction-failed:com.wallet.common.event.TransactionFailedEvent
```

**Deliverables:**
- [x] Zipkin running in docker-compose
- [x] All services configured with Micrometer tracing
- [x] Trace IDs propagate through Kafka messages (auto with Spring Kafka)
- [x] Dashboard accessible at http://localhost:9411

---

### 0.2 Kafka Dead Letter Queue (DLQ) Infrastructure

**Why Now:** Without DLQ, poison messages cause infinite retry loops and block consumer progress.

**Implementation:**

1. Create shared error handling configuration in `common` module:

```java
// common/src/main/java/com/wallet/common/kafka/KafkaErrorConfig.java
@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Publish to DLT after 3 retries
        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        // Retry 3 times with exponential backoff
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(10000L); // Max 10 seconds total

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Don't retry these - send directly to DLT
        handler.addNotRetryableExceptions(
            DeserializationException.class,
            MessageConversionException.class,
            ValidationException.class
        );

        return handler;
    }
}
```

2. Add DLT topics to Kafka configuration

**Deliverables:**
- [x] Shared `KafkaErrorConfig` in common module
- [x] DLT topics auto-created for each consumer topic
- [x] Non-retryable exceptions configured
- [x] DLT monitoring in Kafka UI (available at http://localhost:8090)

---

### 0.3 Common Idempotency Pattern

**Why Now:** Every Kafka consumer needs idempotency. Define the pattern once.

**Implementation:**

1. Create idempotency abstraction in `common` module:

```java
// common/src/main/java/com/wallet/common/idempotency/IdempotencyChecker.java
public interface IdempotencyChecker {

    /**
     * Check if event was already processed.
     * @param eventId Unique identifier for the event
     * @return true if already processed, false if new
     */
    boolean isProcessed(String eventId);

    /**
     * Mark event as processed.
     * @param eventId Unique identifier for the event
     */
    void markProcessed(String eventId);

    /**
     * Mark event as processed with outcome tracking.
     */
    void markProcessed(String eventId, ProcessingOutcome outcome);
}

public record ProcessingOutcome(
    boolean success,
    String errorMessage,
    Instant processedAt
) {}
```

2. Each service implements with its own storage:

```java
// Example: Notification service implementation
@Repository
public class NotificationIdempotencyRepository implements IdempotencyChecker {

    private final NotificationLogRepository repository;

    @Override
    public boolean isProcessed(String eventId) {
        return repository.existsByEventId(UUID.fromString(eventId));
    }

    @Override
    public void markProcessed(String eventId) {
        NotificationLog log = new NotificationLog();
        log.setEventId(UUID.fromString(eventId));
        log.setProcessedAt(Instant.now());
        repository.save(log);
    }
}
```

**Deliverables:**
- [x] `IdempotencyChecker` interface in common module
- [x] Base entity `ProcessedEvent` for services to extend
- [x] Idempotency implemented in transaction-service (via `idempotencyKey` field)
- [x] Idempotency implemented in ledger-service (via `transactionId` uniqueness)

---

## Phase 1: Ledger Service (Double-Entry Bookkeeping) - THE CORE

**Why Ledger First?**
- The Ledger is the most complex domain logic. It dictates the data quality for everything else.
- Notification is "fire and forget" - it's easy and can be built later.
- If you build Notification first, you'll spend time debugging email templates. If you build Ledger first, you spend time validating the financial core.

**Goal:** Create an immutable, auditable record of all financial movements using proper accounting principles.

### 1.1 Double-Entry Data Model

**Why Double-Entry?**
- Every transaction has two sides: money comes FROM somewhere and goes TO somewhere
- System accounts represent external entities (Bank, Fees, etc.)
- Allows mathematical proof that the system is zero-sum: `SUM(DEBIT) = SUM(CREDIT)`

```java
public enum EntrySide {
    DEBIT,   // Increases assets, decreases liabilities
    CREDIT   // Decreases assets, increases liabilities
}

public enum AccountType {
    USER_WALLET,    // User's wallet account
    SYSTEM_BANK,    // External bank funding source
    SYSTEM_FEES,    // Fee collection account
    SYSTEM_SUSPENSE // Temporary holding account
}

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String accountNumber;  // Human-readable identifier

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountType type;

    @Column
    private UUID externalId;  // Links to walletId for USER_WALLET accounts

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant createdAt;
}

@Entity
@Table(name = "ledger_entries",
       indexes = {
           @Index(name = "idx_ledger_transaction_id", columnList = "transactionId", unique = true),
           @Index(name = "idx_ledger_account", columnList = "accountId"),
           @Index(name = "idx_ledger_recorded_at", columnList = "recordedAt")
       })
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID transactionId;  // IDEMPOTENCY KEY - from transaction-service

    @Column(nullable = false)
    private UUID journalId;  // Groups related entries together

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private LedgerAccount account;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;  // Always POSITIVE - side indicates direction

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntrySide side;  // DEBIT or CREDIT

    @Column(nullable = false)
    private String currency;

    @Column
    private String description;

    // Informational - from source event, NOT calculated here
    @Column(precision = 19, scale = 4)
    private BigDecimal reportedBalanceAfter;

    @Column(nullable = false)
    private Instant recordedAt;

    // Audit fields
    @Column(nullable = false)
    private String sourceEvent;  // "transaction-completed", "transaction-failed"

    @Column(nullable = false)
    private Instant eventTimestamp;  // When the original event occurred
}

@Entity
@Table(name = "ledger_journals")
public class LedgerJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String reference;  // External reference (transaction ID, etc.)

    @Column
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "journalId", cascade = CascadeType.ALL)
    private List<LedgerEntry> entries;

    // Validation: Debits must equal Credits
    public boolean isBalanced() {
        BigDecimal debits = entries.stream()
            .filter(e -> e.getSide() == EntrySide.DEBIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal credits = entries.stream()
            .filter(e -> e.getSide() == EntrySide.CREDIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return debits.compareTo(credits) == 0;
    }
}
```

### 1.2 Transaction Type Mappings

```
DEPOSIT ($100 to User Wallet):
┌─────────────────────────────────────────────────────┐
│ Journal: "Deposit to wallet abc123"                 │
├─────────────────────────────────────────────────────┤
│ DEBIT   | User Wallet (abc123)  | $100.00          │
│ CREDIT  | SYSTEM_BANK           | $100.00          │
└─────────────────────────────────────────────────────┘
Sum: DEBIT=$100, CREDIT=$100 ✓ Balanced

WITHDRAWAL ($50 from User Wallet):
┌─────────────────────────────────────────────────────┐
│ Journal: "Withdrawal from wallet abc123"            │
├─────────────────────────────────────────────────────┤
│ DEBIT   | SYSTEM_BANK           | $50.00           │
│ CREDIT  | User Wallet (abc123)  | $50.00           │
└─────────────────────────────────────────────────────┘
Sum: DEBIT=$50, CREDIT=$50 ✓ Balanced

TRANSFER ($25 from Wallet A to Wallet B):
┌─────────────────────────────────────────────────────┐
│ Journal: "Transfer A → B"                           │
├─────────────────────────────────────────────────────┤
│ DEBIT   | User Wallet B         | $25.00           │
│ CREDIT  | User Wallet A         | $25.00           │
└─────────────────────────────────────────────────────┘
Sum: DEBIT=$25, CREDIT=$25 ✓ Balanced
```

### 1.3 System Accounts Seeding (Critical)

**Why This Matters:** The double-entry logic requires system accounts (SYSTEM_BANK, SYSTEM_FEES, SYSTEM_SUSPENSE) to exist before any transactions can be recorded. Without them, the first deposit will fail with `EntityNotFoundException`.

```java
// ledger-service/src/main/java/com/wallet/ledger/config/SystemAccountSeeder.java
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemAccountSeeder implements CommandLineRunner {

    private final LedgerAccountRepository accountRepository;

    // System account identifiers - consistent across environments
    private static final String SYSTEM_BANK_ACCOUNT = "SYSTEM-BANK-001";
    private static final String SYSTEM_FEES_ACCOUNT = "SYSTEM-FEES-001";
    private static final String SYSTEM_SUSPENSE_ACCOUNT = "SYSTEM-SUSPENSE-001";

    // Supported currencies - add more as needed
    private static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP");

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking system accounts...");

        for (String currency : CURRENCIES) {
            ensureSystemAccount(SYSTEM_BANK_ACCOUNT, AccountType.SYSTEM_BANK, currency,
                "External bank funding source");
            ensureSystemAccount(SYSTEM_FEES_ACCOUNT, AccountType.SYSTEM_FEES, currency,
                "Fee collection account");
            ensureSystemAccount(SYSTEM_SUSPENSE_ACCOUNT, AccountType.SYSTEM_SUSPENSE, currency,
                "Temporary holding account for reconciliation");
        }

        log.info("System accounts verified/created successfully");
    }

    private void ensureSystemAccount(String accountNumber, AccountType type,
                                      String currency, String description) {
        boolean exists = accountRepository.existsByAccountNumberAndCurrency(accountNumber, currency);

        if (!exists) {
            LedgerAccount account = LedgerAccount.builder()
                .accountNumber(accountNumber + "-" + currency)
                .type(type)
                .currency(currency)
                .description(description)
                .createdAt(Instant.now())
                .build();

            accountRepository.save(account);
            log.info("Created system account: {} ({})", accountNumber, currency);
        } else {
            log.debug("System account already exists: {} ({})", accountNumber, currency);
        }
    }
}
```

**Test for System Account Seeding:**

```java
@SpringBootTest
@Testcontainers
class SystemAccountSeederTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LedgerAccountRepository accountRepository;

    @Test
    void shouldCreateSystemAccountsOnStartup() {
        // Verify SYSTEM_BANK accounts exist for all currencies
        assertThat(accountRepository.findByAccountNumberAndCurrency("SYSTEM-BANK-001-USD", "USD"))
            .isPresent();
        assertThat(accountRepository.findByAccountNumberAndCurrency("SYSTEM-BANK-001-EUR", "EUR"))
            .isPresent();

        // Verify SYSTEM_FEES accounts
        assertThat(accountRepository.findByType(AccountType.SYSTEM_FEES))
            .hasSize(3); // USD, EUR, GBP

        // Verify SYSTEM_SUSPENSE accounts
        assertThat(accountRepository.findByType(AccountType.SYSTEM_SUSPENSE))
            .hasSize(3);
    }

    @Test
    void shouldNotDuplicateAccountsOnRestart() {
        // Simulate app restart by running seeder again
        SystemAccountSeeder seeder = new SystemAccountSeeder(accountRepository);
        seeder.run();

        // Should still only have 3 of each type (not 6)
        assertThat(accountRepository.findByType(AccountType.SYSTEM_BANK))
            .hasSize(3);
    }
}
```

---

### 1.4 Event Processing with Idempotency

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEntryRepository entryRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerJournalRepository journalRepository;

    @Transactional
    public void recordTransaction(TransactionCompletedEvent event) {
        // IDEMPOTENCY: Check if already recorded
        if (entryRepository.existsByTransactionId(event.getTransactionId())) {
            log.info("Transaction {} already recorded in ledger", event.getTransactionId());
            return;
        }

        LedgerJournal journal = createJournal(event);
        List<LedgerEntry> entries = switch (event.getType()) {
            case DEPOSIT -> createDepositEntries(event, journal);
            case WITHDRAWAL -> createWithdrawalEntries(event, journal);
            case TRANSFER_OUT -> createTransferOutEntries(event, journal);
            case TRANSFER_IN -> List.of(); // Recorded with TRANSFER_OUT
        };

        // Validate double-entry balance
        if (!journal.isBalanced()) {
            throw new IllegalStateException(
                "Journal entries not balanced for transaction " + event.getTransactionId()
            );
        }

        journalRepository.save(journal);
        log.info("Recorded {} ledger entries for transaction {}",
            entries.size(), event.getTransactionId());
    }

    private List<LedgerEntry> createDepositEntries(
            TransactionCompletedEvent event,
            LedgerJournal journal) {

        LedgerAccount userAccount = getOrCreateUserAccount(event.getWalletId(), event.getCurrency());
        LedgerAccount bankAccount = getSystemAccount(AccountType.SYSTEM_BANK, event.getCurrency());

        LedgerEntry debit = LedgerEntry.builder()
            .transactionId(event.getTransactionId())
            .journalId(journal.getId())
            .account(userAccount)
            .amount(event.getAmount())  // Store the DELTA, not balance
            .side(EntrySide.DEBIT)
            .currency(event.getCurrency())
            .description("Deposit")
            .reportedBalanceAfter(event.getBalanceAfter())  // Informational only
            .recordedAt(Instant.now())
            .sourceEvent("transaction-completed")
            .eventTimestamp(event.getCompletedAt())
            .build();

        LedgerEntry credit = LedgerEntry.builder()
            .transactionId(event.getTransactionId())
            .journalId(journal.getId())
            .account(bankAccount)
            .amount(event.getAmount())
            .side(EntrySide.CREDIT)
            .currency(event.getCurrency())
            .description("Deposit funding")
            .recordedAt(Instant.now())
            .sourceEvent("transaction-completed")
            .eventTimestamp(event.getCompletedAt())
            .build();

        return List.of(debit, credit);
    }
}
```

### 1.5 Reconciliation Queries

```java
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByTransactionId(UUID transactionId);

    // Get all entries for an account
    List<LedgerEntry> findByAccountIdOrderByRecordedAtDesc(UUID accountId);

    // Calculate account balance from ledger
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN e.side = 'DEBIT' THEN e.amount ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN e.side = 'CREDIT' THEN e.amount ELSE 0 END), 0)
        FROM LedgerEntry e
        WHERE e.account.id = :accountId
        """)
    BigDecimal calculateBalance(@Param("accountId") UUID accountId);

    // Verify system is zero-sum
    @Query("""
        SELECT
            SUM(CASE WHEN e.side = 'DEBIT' THEN e.amount ELSE 0 END) as totalDebits,
            SUM(CASE WHEN e.side = 'CREDIT' THEN e.amount ELSE 0 END) as totalCredits
        FROM LedgerEntry e
        WHERE e.currency = :currency
        """)
    BalanceSummary getSystemBalance(@Param("currency") String currency);

    // Audit: entries between dates
    List<LedgerEntry> findByRecordedAtBetweenOrderByRecordedAt(
        Instant start, Instant end);
}

public record BalanceSummary(BigDecimal totalDebits, BigDecimal totalCredits) {
    public boolean isBalanced() {
        return totalDebits.compareTo(totalCredits) == 0;
    }
}
```

**Deliverables:**
- [x] Double-entry data model (LedgerAccount, LedgerEntry, LedgerJournal)
- [x] System accounts seeded on startup (SystemAccountSeeder with USD support)
- [x] Event listener with idempotency (TransactionEventListener)
- [x] LedgerService with deposit, withdrawal, and transfer entry creation
- [x] Unit tests (LedgerServiceTest - 372 lines)
- [x] Integration tests (LedgerServiceIntegrationTest - 519 lines with TestContainers)

---

## Phase 2: Fraud Analysis Service

**Important Distinction:** This is a **Detective** (async) fraud service, not **Preventative** (sync). Transactions complete before analysis. For production, consider injecting sync checks into transaction-service.

### 2.1 Data Model

```java
@Entity
@Table(name = "fraud_analyses",
       indexes = @Index(name = "idx_fraud_transaction", columnList = "transactionId", unique = true))
public class FraudAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID transactionId;  // IDEMPOTENCY KEY

    @Column(nullable = false)
    private UUID walletId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String transactionType;

    @Column(nullable = false)
    private Integer riskScore;  // 0-100

    @ElementCollection
    @CollectionTable(name = "fraud_risk_factors")
    private List<String> riskFactors;  // ["high_amount", "new_account", "velocity"]

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FraudDecision decision;  // APPROVE, FLAG, BLOCK

    @Column(nullable = false)
    private Instant analyzedAt;

    @Column
    private String notes;
}

public enum FraudDecision {
    APPROVE,  // No action needed
    FLAG,     // Manual review recommended
    BLOCK     // Account should be suspended (future: trigger account-service)
}

@Entity
@Table(name = "fraud_rules")
public class FraudRule {
    @Id
    private String ruleId;
    private String description;
    private String condition;  // SpEL or simple comparison
    private Integer scoreImpact;  // Points to add to risk score
    private boolean active;
}
```

### 2.2 Rule-Based Analysis

```java
@Service
@RequiredArgsConstructor
public class FraudAnalysisService {

    private final FraudAnalysisRepository analysisRepository;
    private final FraudRuleRepository ruleRepository;
    private final TransactionHistoryService historyService;
    private final ApplicationEventPublisher eventPublisher;

    private static final int FLAG_THRESHOLD = 50;
    private static final int BLOCK_THRESHOLD = 80;

    @Transactional
    public FraudAnalysis analyzeTransaction(TransactionCompletedEvent event) {
        // IDEMPOTENCY
        Optional<FraudAnalysis> existing =
            analysisRepository.findByTransactionId(event.getTransactionId());
        if (existing.isPresent()) {
            return existing.get();
        }

        List<String> riskFactors = new ArrayList<>();
        int riskScore = 0;

        // Rule 1: Large transaction amount
        if (event.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            riskScore += 30;
            riskFactors.add("large_amount");
        }

        // Rule 2: Velocity check - many transactions in short time
        long recentCount = historyService.countTransactionsInLastHour(event.getWalletId());
        if (recentCount > 10) {
            riskScore += 25;
            riskFactors.add("high_velocity");
        }

        // Rule 3: New account (wallet created recently)
        if (historyService.isNewWallet(event.getWalletId())) {
            riskScore += 15;
            riskFactors.add("new_account");
        }

        // Rule 4: Unusual transaction pattern
        if (historyService.isUnusualAmount(event.getWalletId(), event.getAmount())) {
            riskScore += 20;
            riskFactors.add("unusual_pattern");
        }

        FraudDecision decision = calculateDecision(riskScore);

        FraudAnalysis analysis = FraudAnalysis.builder()
            .transactionId(event.getTransactionId())
            .walletId(event.getWalletId())
            .userId(event.getUserId())
            .amount(event.getAmount())
            .transactionType(event.getType().name())
            .riskScore(riskScore)
            .riskFactors(riskFactors)
            .decision(decision)
            .analyzedAt(Instant.now())
            .build();

        analysis = analysisRepository.save(analysis);

        // Publish alert for high-risk transactions
        if (decision != FraudDecision.APPROVE) {
            eventPublisher.publishEvent(new FraudAlertEvent(analysis));
        }

        return analysis;
    }

    private FraudDecision calculateDecision(int riskScore) {
        if (riskScore >= BLOCK_THRESHOLD) return FraudDecision.BLOCK;
        if (riskScore >= FLAG_THRESHOLD) return FraudDecision.FLAG;
        return FraudDecision.APPROVE;
    }
}
```

### 2.3 UserBlockedEvent Feedback Loop (Critical for BLOCK decisions)

**Why This Matters:** When the Fraud Service decides to BLOCK a user (risk score >= 80), this decision happens *after* the transaction completes. Without a feedback loop, the user can continue making transactions until someone manually intervenes.

**Solution:** Publish a `UserBlockedEvent` to Kafka that `account-service` listens to, immediately locking the user's account.

```java
// common/src/main/java/com/wallet/common/event/UserBlockedEvent.java
public record UserBlockedEvent(
    UUID userId,
    UUID triggeredByTransactionId,
    String reason,
    int riskScore,
    Instant blockedAt
) {}
```

```java
// fraud-service: Enhanced FraudAnalysisService
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudAnalysisService {

    private final FraudAnalysisRepository analysisRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String USER_BLOCKED_TOPIC = "user-blocked";

    @Transactional
    public FraudAnalysis analyzeTransaction(TransactionCompletedEvent event) {
        // ... existing analysis logic ...

        FraudDecision decision = calculateDecision(riskScore);

        FraudAnalysis analysis = FraudAnalysis.builder()
            // ... existing fields ...
            .build();

        analysis = analysisRepository.save(analysis);

        // CRITICAL: Publish UserBlockedEvent for BLOCK decisions
        if (decision == FraudDecision.BLOCK) {
            publishUserBlockedEvent(event, analysis);
        }

        // Publish fraud-alert for FLAG and BLOCK
        if (decision != FraudDecision.APPROVE) {
            kafkaTemplate.send("fraud-alert", event.getUserId().toString(),
                new FraudAlertEvent(analysis));
        }

        return analysis;
    }

    private void publishUserBlockedEvent(TransactionCompletedEvent event, FraudAnalysis analysis) {
        UserBlockedEvent blockedEvent = new UserBlockedEvent(
            event.getUserId(),
            event.getTransactionId(),
            "Automated fraud detection: " + String.join(", ", analysis.getRiskFactors()),
            analysis.getRiskScore(),
            Instant.now()
        );

        kafkaTemplate.send(USER_BLOCKED_TOPIC, event.getUserId().toString(), blockedEvent)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish UserBlockedEvent for user {}", event.getUserId(), ex);
                } else {
                    log.warn("Published UserBlockedEvent for user {} due to risk score {}",
                        event.getUserId(), analysis.getRiskScore());
                }
            });
    }
}
```

```java
// account-service: Listen for UserBlockedEvent
@Component
@RequiredArgsConstructor
@Slf4j
public class UserBlockedEventListener {

    private final UserRepository userRepository;
    private final UserBlockLogRepository blockLogRepository;

    @KafkaListener(topics = "user-blocked", groupId = "account-service")
    @Transactional
    public void handleUserBlocked(
            @Payload UserBlockedEvent event,
            Acknowledgment ack) {

        UUID userId = event.userId();

        // Idempotency: Check if already processed
        if (blockLogRepository.existsByTriggeredByTransactionId(event.triggeredByTransactionId())) {
            log.info("UserBlockedEvent for transaction {} already processed", event.triggeredByTransactionId());
            ack.acknowledge();
            return;
        }

        // Lock the user account
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        user.setStatus(UserStatus.BLOCKED);
        user.setBlockedAt(event.blockedAt());
        user.setBlockReason(event.reason());
        userRepository.save(user);

        // Log the block event
        UserBlockLog blockLog = UserBlockLog.builder()
            .userId(userId)
            .triggeredByTransactionId(event.triggeredByTransactionId())
            .reason(event.reason())
            .riskScore(event.riskScore())
            .blockedAt(event.blockedAt())
            .build();
        blockLogRepository.save(blockLog);

        log.warn("User {} blocked due to fraud detection. Risk score: {}, Reason: {}",
            userId, event.riskScore(), event.reason());

        ack.acknowledge();
    }
}
```

```java
// account-service: Add UserStatus enum and fields to User entity
public enum UserStatus {
    ACTIVE,
    BLOCKED,
    SUSPENDED,  // Manual suspension
    PENDING_VERIFICATION
}

// In User entity, add:
@Column(nullable = false)
@Enumerated(EnumType.STRING)
private UserStatus status = UserStatus.ACTIVE;

@Column
private Instant blockedAt;

@Column
private String blockReason;
```

```java
// transaction-service: Check user status before processing transactions
@Service
public class TransactionService {

    private final AccountServiceClient accountClient;

    @Transactional
    public TransactionResponse processDeposit(DepositRequest request) {
        // Check if user is blocked BEFORE processing
        UserStatus status = accountClient.getUserStatus(request.getUserId());
        if (status == UserStatus.BLOCKED) {
            throw new UserBlockedException("User account is blocked due to: " +
                accountClient.getBlockReason(request.getUserId()));
        }

        // Proceed with deposit...
    }
}
```

**Test for Feedback Loop:**

```java
@SpringBootTest
@EmbeddedKafka(topics = {"user-blocked", "transaction-completed"})
class FraudBlockFeedbackLoopTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldBlockUserWhenFraudDetected() {
        // Given: A user and a high-risk transaction
        User user = userRepository.save(User.builder()
            .id(UUID.randomUUID())
            .status(UserStatus.ACTIVE)
            .build());

        TransactionCompletedEvent highRiskTransaction = new TransactionCompletedEvent(
            UUID.randomUUID(),
            user.getId(),
            UUID.randomUUID(),
            TransactionType.DEPOSIT,
            new BigDecimal("50000"),  // Large amount triggers fraud
            "USD",
            new BigDecimal("50000"),
            Instant.now()
        );

        // When: Transaction is processed by fraud service
        kafkaTemplate.send("transaction-completed",
            user.getId().toString(), highRiskTransaction);

        // Then: User should be blocked
        await().atMost(10, SECONDS).untilAsserted(() -> {
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.BLOCKED);
            assertThat(updatedUser.getBlockReason()).contains("fraud");
        });
    }
}
```

---

**Deliverables:**
- [ ] FraudAnalysis entity with idempotency
- [ ] Rule-based scoring engine
- [ ] Transaction history queries for velocity checks
- [ ] FraudAlertEvent publishing
- [ ] UserBlockedEvent publishing for BLOCK decisions
- [ ] account-service listener for UserBlockedEvent
- [ ] User status check in transaction-service before processing
- [ ] Integration tests for each rule
- [ ] Integration test for feedback loop
- [ ] Admin API for rule management (optional)

---

## Phase 3: Integration & Observability Hardening

### 3.1 Health Checks & Metrics

```java
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {
        try {
            kafkaAdmin.describeCluster();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

Add to each service:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  health:
    kafka:
      enabled: true
    db:
      enabled: true
```

### 3.2 Prometheus + Grafana Setup

```yaml
# docker-compose.yml additions
prometheus:
  image: prom/prometheus:latest
  ports:
    - "9090:9090"
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml

grafana:
  image: grafana/grafana:latest
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
```

**Deliverables:**
- [ ] Health endpoints for all services
- [ ] Prometheus scraping configured
- [ ] Grafana dashboards for:
  - Transaction throughput
  - Kafka consumer lag
  - Error rates by service
  - Response time percentiles

---

## Phase 4: Production Hardening (Future)

### 4.1 Sync Fraud Checks (Optional Enhancement)

If preventative fraud is required:

```java
// In transaction-service
@Service
public class TransactionService {

    private final FraudClient fraudClient;  // Feign or gRPC

    @Transactional
    public TransactionResponse processDeposit(DepositRequest request) {
        // Pre-transaction fraud check
        FraudCheckResult fraudCheck = fraudClient.checkTransaction(
            request.getWalletId(),
            request.getAmount(),
            TransactionType.DEPOSIT
        );

        if (fraudCheck.isBlocked()) {
            throw new TransactionBlockedException(fraudCheck.getReason());
        }

        // Proceed with deposit...
    }
}
```

### 4.2 Event Schema Versioning

```java
// In common module
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "version")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TransactionCompletedEventV1.class, name = "v1"),
    @JsonSubTypes.Type(value = TransactionCompletedEventV2.class, name = "v2")
})
public abstract class TransactionCompletedEvent {
    // Common fields
}
```

### 4.3 Secret Management

```yaml
# Integrate with HashiCorp Vault or AWS Secrets Manager
spring:
  cloud:
    vault:
      uri: http://vault:8200
      authentication: TOKEN
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
```_

---

## Testing Matrix

| Service | Unit Tests | Integration Tests | Contract Tests |
|---------|------------|-------------------|----------------|
| account-service | ✅ Done | ✅ Done | Planned |
| transaction-service | ✅ Done | ✅ Done | Planned |
| ledger-service | ✅ Done | ✅ Done | Planned |
| notification-service | Planned | Planned (GreenMail) | Planned |
| fraud-service | Planned | Planned | Planned |

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Duplicate notifications | High (without idempotency) | Medium | Idempotency checks + DB constraints |
| Kafka consumer crash loop | Medium | High | DLQ + error handling |
| Ledger inconsistency | Medium (out-of-order events) | Critical | Use delta amounts, not calculated balances |
| Fraud after-the-fact only | Accepted | Medium | Document as "Analysis Service", Phase 5 for sync |
| Schema evolution breaks consumers | Medium | High | Version events, backward compatibility policy |
| Debugging across services | High (without tracing) | High | Implement tracing in Phase 0 |

---

## Appendix A: Event Catalog

| Event | Producer | Consumers | Payload |
|-------|----------|-----------|---------|
| `user-registered` | account-service | notification-service | userId, email, fullName, registeredAt |
| `wallet-created` | account-service | transaction-service, notification-service | walletId, userId, currency, createdAt |
| `transaction-completed` | transaction-service | ledger-service, fraud-service, notification-service | transactionId, walletId, userId, type, amount, currency, balanceAfter, completedAt |
| `transaction-failed` | transaction-service | ledger-service, fraud-service, notification-service | transactionId, walletId, userId, type, amount, reason, failedAt |
| `fraud-alert` | fraud-service | notification-service | analysisId, transactionId, riskScore, decision |
| `user-blocked` | fraud-service | account-service, notification-service | userId, triggeredByTransactionId, reason, riskScore, blockedAt |

---

## Appendix B: Decision Log

| Decision | Rationale | Date |
|----------|-----------|------|
| Detective fraud over preventative | Simpler architecture for MVP; sync checks add latency | 2025-12-05 |
| Database idempotency over Redis | Already have Postgres; one less moving part | 2025-12-05 |
| Double-entry ledger | Industry standard for financial audit; enables reconciliation | 2025-12-05 |
| Store delta not calculated balance | Avoids race conditions from out-of-order event processing | 2025-12-05 |
| Tracing in Phase 0 | Retrofitting is 10x harder; debug value is immediate | 2025-12-05 |
| Defer Schema Registry | Mono-repo provides compile-time safety; add when services split | 2025-12-05 |

---

## Appendix C: Changelog

### Version 3.0 (2025-12-07)
**Phase 0 & 1 Complete**

- **Ledger Service Implementation:**
  - Double-entry bookkeeping with LedgerAccount, LedgerEntry, LedgerJournal entities
  - System account seeding (SYSTEM_BANK, SYSTEM_FEES, SYSTEM_SUSPENSE for USD)
  - TransactionEventListener consuming `transaction-completed` events
  - Idempotency via transactionId uniqueness in journal
  - Comprehensive test coverage (unit + integration with TestContainers)

- **Transaction Service Enhancements:**
  - Added transfer operations (P2P with dual entries: TRANSFER_OUT + TRANSFER_IN)
  - Query endpoints: paginated transaction history, balance lookup
  - Event publishing: `transaction-completed`, `transaction-failed`
  - WalletEventListener for `wallet-created` → WalletBalance initialization

- **Cross-Cutting Concerns:**
  - Distributed tracing fully operational across all services
  - DLQ infrastructure with exponential backoff and non-retryable exceptions
  - Idempotency patterns implemented in transaction and ledger services

- **Code Quality:**
  - Removed unused imports and variables (fd13d81)
  - All services have comprehensive unit and integration tests

### Version 2.1 (2025-12-05)
- Initial revised plan addressing idempotency, DLQ, and observability concerns
- Phase 0 infrastructure defined
- Phase 1-5 roadmap established