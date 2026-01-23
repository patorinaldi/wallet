# Notification Service Implementation Plan (Phase 3)

## Executive Summary

Implement a notification service that consumes Kafka events and sends email notifications to users and administrators. The service follows existing architectural patterns (event-driven, idempotent processing, TestContainers testing) and reuses infrastructure from Phase 0 (KafkaErrorConfig, IdempotencyChecker).

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Email Rendering** | Spring Mail + Thymeleaf | Mail for sending, Thymeleaf for HTML templates |
| **User Email Resolution** | Local UserInfo cache | Follows `BlockedUser` pattern, services stay decoupled |
| **Email Testing** | GreenMail | In-memory, fast, full email verification API |
| **Admin Notifications** | Configurable admin email | Fraud alerts sent to `notification.admin-email` |
| **Local Dev Email** | MailHog in Docker Compose | Web UI at port 8025 for visual verification |
| **Error Handling** | KafkaErrorConfig from common | Reuse Phase 0 DLQ + exponential backoff |
| **Idempotency** | Implement IdempotencyChecker interface | Follow common module pattern |
| **Event DTOs** | Use common module events | DO NOT create new event classes |

---

## Critical: Reuse from Common Module

**DO NOT create new event classes.** Use existing events from `:common` module:
- `com.patorinaldi.wallet.common.event.UserRegisteredEvent`
- `com.patorinaldi.wallet.common.event.TransactionCompletedEvent`
- `com.patorinaldi.wallet.common.event.TransactionFailedEvent`
- `com.patorinaldi.wallet.common.event.UserBlockedEvent`
- `com.patorinaldi.wallet.common.event.FraudAlertEvent`
- `com.patorinaldi.wallet.common.event.WalletCreatedEvent`

**Reuse infrastructure:**
- `com.patorinaldi.wallet.common.kafka.KafkaErrorConfig` - DLQ + retry logic
- `com.patorinaldi.wallet.common.idempotency.IdempotencyChecker` - Interface to implement

---

## Notifications to Implement

| Notification | Kafka Topic | Recipient | Priority | Idempotency Key |
|--------------|-------------|-----------|----------|-----------------|
| Welcome Email | `user-registered` | User | P1 | `recordId` |
| Transaction Receipt | `transaction-completed` | User | P1 | `transactionId` |
| Transaction Failed | `transaction-failed` | User | P1 | `transactionId` |
| Account Blocked | `user-blocked` | User | P1 | `triggeredByTransactionId` |
| Fraud Alert | `fraud-alert` | Admin | P2 | `analysisId` |
| Wallet Created | `wallet-created` | User | P3 (optional) | `walletId` |

---

## Known Limitations (MVP)

1. **No UserUpdatedEvent**: If a user changes their email in account-service, notification-service will continue using the cached (old) email. Future enhancement: account-service should publish `UserUpdatedEvent`.

2. **Eventually Consistent Cache**: UserInfo cache is populated from `user-registered` events. There's a brief window where user data may not be available.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    notification-service (8084)                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │ Event Listeners  │    │   Email Service  │                  │
│  │                  │    │                  │                  │
│  │ - UserRegistered │───▶│ - sendWelcome()  │──▶ SMTP/MailHog  │
│  │ - Transaction    │    │ - sendTxReceipt()│                  │
│  │ - UserBlocked    │    │ - sendBlocked()  │                  │
│  │ - FraudAlert     │    │ - sendFraudAlert │                  │
│  └────────┬─────────┘    └──────────────────┘                  │
│           │                       ▲                             │
│           ▼                       │                             │
│  ┌──────────────────┐    ┌───────┴──────────┐                  │
│  │ UserInfo Cache   │    │ Thymeleaf        │                  │
│  │ (from user-      │    │ Templates        │                  │
│  │  registered)     │    │ /templates/*.html│                  │
│  └──────────────────┘    └──────────────────┘                  │
│                                                                 │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │ NotificationLog  │    │ Idempotency      │                  │
│  │ (audit trail)    │    │ (eventId unique) │                  │
│  └──────────────────┘    └──────────────────┘                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Flow

### User Registration → Welcome Email
```
account-service publishes UserRegisteredEvent
    → notification-service consumes from "user-registered"
    → Caches UserInfo (userId, email, fullName)
    → Renders welcome.html template
    → Sends email via SMTP
    → Logs to NotificationLog (idempotency)
```

### Transaction → Receipt Email
```
transaction-service publishes TransactionCompletedEvent
    → notification-service consumes from "transaction-completed"
    → Looks up UserInfo by userId (from cache)
    → Renders transaction-receipt.html template
    → Sends email via SMTP
    → Logs to NotificationLog
```

### Fraud Detection → Notifications
```
fraud-service publishes UserBlockedEvent + FraudAlertEvent
    → UserBlockedEventListener:
        → Looks up UserInfo by userId
        → Sends "Account Blocked" email to user
    → FraudAlertEventListener:
        → Sends alert email to admin (from config)
```

---

## Race Condition Handling (Ghost User Problem)

**Problem:** A `transaction-completed` event may arrive BEFORE `user-registered` is processed (Kafka partitions are independent).

**Solution:** Throw `RetryableException` when UserInfo is not found. This triggers KafkaErrorConfig's exponential backoff, giving time for the user event to be processed.

### Transaction Events (Retry on Missing User)
```java
@KafkaListener(topics = "transaction-completed", groupId = "notification-service")
public void handleTransaction(TransactionCompletedEvent event) {
    // Idempotency check first
    if (idempotencyChecker.isProcessed(event.transactionId().toString())) {
        return;
    }

    // Lookup user - throw retryable if not found (user MUST exist for transactions)
    UserInfo user = userInfoRepository.findById(event.userId())
        .orElseThrow(() -> new RetryableException(
            "UserInfo not found for userId: " + event.userId() + ". Will retry."));

    // Send email - EmailService saves NotificationLog internally
    emailService.sendTransactionReceipt(user, event);
}
```

### UserBlockedEvent (Don't Retry - User May Not Exist)
```java
@KafkaListener(topics = "user-blocked", groupId = "notification-service")
public void handleUserBlocked(UserBlockedEvent event) {
    // Idempotency check
    if (idempotencyChecker.isProcessed(event.triggeredByTransactionId().toString())) {
        return;
    }

    // User may legitimately not exist (pre-blocked bad actors)
    // Don't throw RetryableException - just log and skip
    // EmailService saves NotificationLog internally when email is sent
    userInfoRepository.findById(event.userId())
        .ifPresentOrElse(
            user -> emailService.sendAccountBlockedNotification(user, event),
            () -> log.warn("Cannot notify blocked user {} - no cached UserInfo", event.userId())
        );
}
```

### UserInfo Cache Concurrency (Upsert Pattern)
```java
@KafkaListener(topics = "user-registered", groupId = "notification-service")
@Transactional
public void handleUserRegistered(UserRegisteredEvent event) {
    // Idempotency: Use upsert to handle burst of events for same user
    UserInfo user = userInfoRepository.findById(event.userId())
        .orElseGet(() -> userInfoRepository.save(UserInfo.builder()
            .userId(event.userId())
            .email(event.email())
            .fullName(event.fullName())
            .createdAt(Instant.now())
            .build()));

    // Send welcome email - EmailService saves NotificationLog internally
    emailService.sendWelcomeEmail(user, event);
}
```

---

## Implementation Steps

### Step 1: Update Dependencies
**File:** `notification-service/build.gradle.kts`

```gradle
plugins {
    id("org.springframework.boot")
}

dependencies {
    // CRITICAL: Shared module for DTOs, Events, and Error Handling
    implementation(project(":common"))

    // Web (health checks, actuator)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Email: Mail for sending, Thymeleaf for templates
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Persistence (UserInfo cache, NotificationLog)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Kafka
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Observability (Phase 0 requirement)
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    // Development
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.icegreen:greenmail-junit5:2.0.1")
    testImplementation("org.awaitility:awaitility:4.2.0")
}
```

### Step 2: Create Entities

**UserInfo Entity** (cache of user data)
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/entity/UserInfo.java
```
- `userId` (UUID, PK)
- `email` (String)
- `fullName` (String)
- `createdAt` (Instant)

**NotificationLog Entity** (idempotency + audit)
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/entity/NotificationLog.java
```
- `id` (UUID, auto)
- `eventId` (UUID, unique) - idempotency key (natural business key from event)
- `notificationType` (enum: WELCOME, WALLET_CREATED, TRANSACTION, TRANSACTION_FAILED, BLOCKED, FRAUD_ALERT)
- `recipientEmail` (String)
- `subject` (String)
- `status` (enum: PENDING, SENT, FAILED)
- `errorMessage` (String, nullable)
- `sentAt` (Instant)
- `sourceUserId` (UUID, nullable) - Reference for audit/debugging
- `sourceTransactionId` (UUID, nullable) - Reference for transaction-related notifications

### Step 3: Create Repositories
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/repository/
├── UserInfoRepository.java
└── NotificationLogRepository.java
```

### Step 4: Implement IdempotencyChecker

**File:** `notification-service/src/main/java/com/patorinaldi/wallet/notification/service/NotificationIdempotencyService.java`

Implement the interface from common module. **Simplified pattern:** Only `isProcessed()` is used - `EmailService` saves `NotificationLog` directly with all fields populated.

```java
import com.patorinaldi.wallet.common.idempotency.IdempotencyChecker;

@Service
@RequiredArgsConstructor
public class NotificationIdempotencyService implements IdempotencyChecker {

    private final NotificationLogRepository repository;

    @Override
    public boolean isProcessed(String eventId) {
        return repository.existsByEventId(UUID.fromString(eventId));
    }

    @Override
    public void markProcessed(String eventId) {
        // Not used - EmailService saves NotificationLog directly with full context
    }

    @Override
    public void markProcessed(String eventId, ProcessingOutcome outcome) {
        // Not used - EmailService saves NotificationLog directly with full context
    }
}
```

**Why this pattern?** The `EmailService` has access to all notification metadata (type, recipient, subject, sourceUserId, sourceTransactionId) that `NotificationLog` requires. Passing all this through `markProcessed()` would be awkward. Instead, `EmailService` saves the complete log after sending.

### Step 5: Create RetryableException

**File:** `notification-service/src/main/java/com/patorinaldi/wallet/notification/exception/RetryableException.java`

```java
/**
 * Thrown when an operation should be retried (e.g., UserInfo not yet cached).
 * KafkaErrorConfig will apply exponential backoff before retrying.
 */
public class RetryableException extends RuntimeException {
    public RetryableException(String message) {
        super(message);
    }
}
```

### Step 6: Create Event Listeners

All listeners use events from `com.patorinaldi.wallet.common.event.*` (NOT new classes).

**UserRegisteredEventListener** - Caches user info + sends welcome email
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/event/UserRegisteredEventListener.java
```
- Consumes `UserRegisteredEvent` from common module
- Saves UserInfo to cache using upsert pattern (handles concurrent events)
- Sends welcome email
- No race condition risk (this populates the cache)

**WalletCreatedEventListener** - Notifies user of new wallet
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/event/WalletCreatedEventListener.java
```
- Consumes `WalletCreatedEvent` from common module
- Looks up UserInfo (throws RetryableException if not found)
- Sends wallet created notification email

**TransactionCompletedEventListener** - Sends transaction receipts
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/event/TransactionCompletedEventListener.java
```
- Consumes `TransactionCompletedEvent` from common module
- Looks up UserInfo (throws RetryableException if not found)
- Sends transaction receipt email

**TransactionFailedEventListener** - Notifies user of failed transactions
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/event/TransactionFailedEventListener.java
```
- Consumes `TransactionFailedEvent` from common module
- Looks up UserInfo (throws RetryableException if not found)
- Sends transaction failed notification with error reason

**UserBlockedEventListener** - Notifies user of account block
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/event/UserBlockedEventListener.java
```
- Consumes `UserBlockedEvent` from common module
- Looks up UserInfo (logs warning if not found - does NOT retry)
- Sends account blocked notification if user exists

**FraudAlertEventListener** - Notifies admin
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/event/FraudAlertEventListener.java
```
- Consumes `FraudAlertEvent` from common module
- Sends to configurable admin email (no user lookup needed)

### Step 7: Create Email Service
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/service/EmailService.java
```

**Key Responsibility:** Each method sends the email AND saves `NotificationLog` with full context (providing idempotency).

Methods (all use events from common module as parameters):
- `sendWelcomeEmail(UserInfo user, UserRegisteredEvent event)`
- `sendWalletCreatedEmail(UserInfo user, WalletCreatedEvent event)`
- `sendTransactionReceipt(UserInfo user, TransactionCompletedEvent event)`
- `sendTransactionFailedNotification(UserInfo user, TransactionFailedEvent event)`
- `sendAccountBlockedNotification(UserInfo user, UserBlockedEvent event)`
- `sendFraudAlertToAdmin(FraudAlertEvent event)`

Each method follows this pattern:

**IMPORTANT: Save PENDING first to prevent duplicate emails on crash/retry scenarios.**

If we save the log AFTER sending the email, a crash between send and save would cause Kafka to retry, resulting in duplicate emails. By saving with PENDING status first, the idempotency check catches retries.

```java
@Transactional
public void sendTransactionReceipt(UserInfo user, TransactionCompletedEvent event) {
    String subject = "Transaction Receipt - " + event.type();
    String body = renderTemplate("transaction-receipt", Map.of("user", user, "event", event));

    // 1. Save NotificationLog with PENDING status FIRST (idempotency marker)
    NotificationLog log = notificationLogRepository.save(NotificationLog.builder()
        .eventId(event.transactionId())  // Idempotency key
        .notificationType(NotificationType.TRANSACTION)
        .recipientEmail(user.getEmail())
        .subject(subject)
        .sourceUserId(event.userId())
        .sourceTransactionId(event.transactionId())
        .status(NotificationStatus.PENDING)
        .build());

    try {
        // 2. Send email (external system, outside DB transaction)
        sendEmail(user.getEmail(), subject, body);

        // 3. Update status to SENT
        log.setStatus(NotificationStatus.SENT);
        log.setSentAt(Instant.now());
        notificationLogRepository.save(log);

        meterRegistry.counter("notification.sent", "type", "TRANSACTION").increment();
    } catch (Exception e) {
        // 4. Mark as FAILED if email sending fails
        log.setStatus(NotificationStatus.FAILED);
        log.setErrorMessage(e.getMessage());
        notificationLogRepository.save(log);

        meterRegistry.counter("notification.failed", "type", "TRANSACTION", "reason", e.getClass().getSimpleName()).increment();
        throw e;  // Re-throw to trigger Kafka retry via KafkaErrorConfig
    }
}
```

**Why this works:**
- On retry after crash: `isProcessed(eventId)` returns `true` (PENDING log exists) → skip
- On email failure: Log is marked FAILED, exception re-thrown → Kafka retries
- On success: Log is updated to SENT with timestamp

### Step 8: Create Thymeleaf Templates

**SECURITY NOTE:** Always use `th:text` (not `th:utext`) to auto-escape user-provided data and prevent XSS/injection.

```
notification-service/src/main/resources/templates/
├── welcome.html
├── wallet-created.html
├── transaction-receipt.html
├── transaction-failed.html
├── account-blocked.html
└── fraud-alert.html
```

### Step 9: Update Configuration
**File:** `notification-service/src/main/resources/application.yml`

Add:
- Database connection (PostgreSQL)
- Thymeleaf configuration
- Admin email for fraud alerts
- Mail sender configuration

### Step 10: Update Docker Compose
**File:** `compose.yaml`

Add MailHog service for local development:
```yaml
mailhog:
  image: mailhog/mailhog:latest
  container_name: wallet-mailhog
  ports:
    - "1025:1025"  # SMTP
    - "8025:8025"  # Web UI
```

### Step 11: Create Tests

**Unit Tests:**
- `EmailServiceTest.java` - Mock JavaMailSender, verify template rendering + NotificationLog persistence
- `UserRegisteredEventListenerTest.java` - Test cache population (upsert) + welcome email
- `TransactionCompletedEventListenerTest.java` - Test retry on missing user (RetryableException)
- `TransactionFailedEventListenerTest.java` - Test failed transaction notification + retry
- `NotificationIdempotencyServiceTest.java` - Test idempotency check (isProcessed only)

**Integration Tests:**
- `NotificationServiceIntegrationTest.java` - Full flow with GreenMail + TestContainers
- Test idempotency (duplicate events → single email)
- Test race condition handling (RetryableException → backoff → success)
- Test email content verification

---

## Files to Create/Modify

### New Files (28 files)
```
notification-service/src/main/java/com/patorinaldi/wallet/notification/
├── entity/
│   ├── UserInfo.java
│   ├── NotificationLog.java
│   ├── NotificationType.java (enum)
│   └── NotificationStatus.java (enum: PENDING, SENT, FAILED)
├── repository/
│   ├── UserInfoRepository.java
│   └── NotificationLogRepository.java
├── service/
│   ├── EmailService.java
│   └── NotificationIdempotencyService.java (implements IdempotencyChecker)
├── event/
│   ├── UserRegisteredEventListener.java
│   ├── WalletCreatedEventListener.java
│   ├── TransactionCompletedEventListener.java
│   ├── TransactionFailedEventListener.java
│   ├── UserBlockedEventListener.java
│   └── FraudAlertEventListener.java
├── exception/
│   └── RetryableException.java

notification-service/src/main/resources/
├── templates/
│   ├── welcome.html
│   ├── wallet-created.html
│   ├── transaction-receipt.html
│   ├── transaction-failed.html
│   ├── account-blocked.html
│   └── fraud-alert.html

notification-service/src/test/java/com/patorinaldi/wallet/notification/
├── service/
│   ├── EmailServiceTest.java
│   └── NotificationIdempotencyServiceTest.java
├── event/
│   ├── UserRegisteredEventListenerTest.java
│   ├── TransactionCompletedEventListenerTest.java
│   └── TransactionFailedEventListenerTest.java
├── NotificationServiceIntegrationTest.java

notification-service/src/test/resources/
└── application-test.yml
```

### Files to Modify (2 files)
```
notification-service/build.gradle.kts          # Update dependencies
notification-service/src/main/resources/application.yml  # Add DB, Thymeleaf, notification config
```

### Infrastructure Modification
```
compose.yaml                                    # Add MailHog service
```

---

## Implementation Order

| Step | Task | Key Files |
|------|------|-----------|
| 1 | **Dependencies**: Update build.gradle.kts with all dependencies | `build.gradle.kts` |
| 2 | **Docker Compose**: Add MailHog for local dev (enables manual testing early) | `compose.yaml` |
| 3 | **Configuration**: Update application.yml (DB, mail, thymeleaf) | `application.yml` |
| 4 | **Entities**: UserInfo, NotificationLog, enums | 4 files in `entity/` |
| 5 | **Repositories**: Data access layer | 2 files in `repository/` |
| 6 | **Exception**: RetryableException for race condition handling | `RetryableException.java` |
| 7 | **Idempotency**: NotificationIdempotencyService (implements IdempotencyChecker) | `NotificationIdempotencyService.java` |
| 8 | **Email Service**: Thymeleaf rendering + JavaMailSender + metrics | `EmailService.java` |
| 9 | **Templates**: HTML email templates (use `th:text` for security) | 6 files in `templates/` |
| 10 | **UserRegisteredListener**: Cache user (upsert) + welcome email | `UserRegisteredEventListener.java` |
| 11 | **WalletCreatedListener**: Wallet created notification (with retry) | `WalletCreatedEventListener.java` |
| 12 | **TransactionCompletedListener**: Transaction receipts (with retry) | `TransactionCompletedEventListener.java` |
| 13 | **TransactionFailedListener**: Failed transaction notifications | `TransactionFailedEventListener.java` |
| 14 | **UserBlockedListener**: Account blocked (no retry if user missing) | `UserBlockedEventListener.java` |
| 15 | **FraudAlertListener**: Admin alerts | `FraudAlertEventListener.java` |
| 16 | **Unit Tests**: Services and listeners with mocks | 5 test files |
| 17 | **Integration Tests**: GreenMail + TestContainers + retry testing | `NotificationServiceIntegrationTest.java` |

---

## Configuration to Add

**application.yml additions:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wallet_db
    username: ${DB_USERNAME:wallet}
    password: ${DB_PASSWORD:wallet123}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    mode: HTML
    cache: false  # Disable in dev for hot reload

  mail:
    host: localhost
    port: 1025  # MailHog
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false

notification:
  admin-email: admin@wallet.com
  from-email: noreply@wallet.com
  from-name: Wallet Notifications
```

**Note:** KafkaErrorConfig from common module is auto-configured via component scanning (`@ComponentScan(basePackages = "com.patorinaldi.wallet")`). This provides:
- Dead Letter Queue (*.DLT topics)
- Exponential backoff (1s, 2s, 4s... max 10s)
- Non-retryable exceptions (DeserializationException, etc.)
- RetryableException will trigger backoff + retry

---

## Metrics (Observability)

Add Micrometer counters in `EmailService` for monitoring:

```java
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationLogRepository notificationLogRepository;
    private final MeterRegistry meterRegistry;

    public void sendWelcomeEmail(UserInfo user, UserRegisteredEvent event) {
        try {
            // ... send email + save NotificationLog (see Step 7 for full pattern) ...
            meterRegistry.counter("notification.sent", "type", "WELCOME").increment();
        } catch (Exception e) {
            meterRegistry.counter("notification.failed", "type", "WELCOME", "reason", e.getClass().getSimpleName()).increment();
            throw e;
        }
    }
}
```

**Metrics exposed:**
- `notification.sent{type=WELCOME|TRANSACTION|TRANSACTION_FAILED|BLOCKED|FRAUD_ALERT}`
- `notification.failed{type=..., reason=...}`

These metrics are automatically exposed via `/actuator/prometheus` endpoint.

---

## Email Failure Handling

**Strategy:** Rely on Kafka retry mechanism (via KafkaErrorConfig).

1. If email send fails → exception thrown
2. KafkaErrorConfig catches → exponential backoff retry
3. After max retries → message sent to DLT (Dead Letter Topic)
4. NotificationLog records `status=FAILED` with `errorMessage`

**No separate retry job for MVP.** DLT messages can be manually reprocessed or monitored for alerts.

---

## Future Enhancements (Not in Scope)

- SMS notifications
- Push notifications
- Notification preferences per user
- Email delivery tracking/webhooks
- External email service (SendGrid/SES)
- Rate limiting
- Custom health indicators (SMTP connectivity, cache warmth)
- Notification deduplication window (batch digest)
- Template externalization (database/CMS)
- Contract tests

---

# Integration & Observability Hardening (Phase 3 - Part 2)

## Executive Summary

Enhance the observability of the entire microservices ecosystem by implementing standardized logging, distributed tracing correlation (MDC), and comprehensive metrics collection via Prometheus and Grafana. This ensures the system is production-ready and debuggable.

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Metrics Collection** | Prometheus | Industry standard, pull-based model works well with K8s/Docker |
| **Visualization** | Grafana | Rich ecosystem of dashboards for Spring Boot & Micrometer |
| **Log Format** | Logback (XML) | Standard Spring Boot logging, easy to configure async appenders |
| **Correlation** | MDC + Zipkin | Correlate logs with traces across service boundaries |
| **Sensitive Data** | Masking Utility | Prevent PII (emails) from leaking into logs |

---

## Implementation Steps

### Step 1: Health Checks & Metrics Configuration

**Action:** Update `application.yml` in **ALL** services (`account`, `transaction`, `ledger`, `fraud`, `notification`).

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

**Action:** Create `KafkaHealthIndicator` in each service (or common module if possible, but usually service-specific bean).

```java
@Component
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {
    private final KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {
        try {
            // Use nodes() instead of clusterId() - ensures brokers are actually reachable
            Collection<Node> nodes = kafkaAdmin.describeCluster().nodes().get(5, TimeUnit.SECONDS);
            return Health.up()
                .withDetail("nodes", nodes.size())
                .build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

### Step 2: Prometheus & Grafana Setup

**Action:** Update `compose.yaml` to include monitoring infrastructure.

```yaml
  prometheus:
    image: prom/prometheus:latest
    container_name: wallet-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    networks:
      - wallet-network

  grafana:
    image: grafana/grafana:latest
    container_name: wallet-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    networks:
      - wallet-network
```

**Action:** Create `prometheus.yml` in root.

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'wallet-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081', 'host.docker.internal:8082', 'host.docker.internal:8083', 'host.docker.internal:8084', 'host.docker.internal:8085']
```

**Note for Linux users:** `host.docker.internal` only works on Mac/Windows by default. For Linux, add to compose.yaml:
```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

**Action:** Add Prometheus registry dependency to **ALL** service `build.gradle.kts` files:
```gradle
implementation("io.micrometer:micrometer-registry-prometheus")
```

### Step 3: Logging Infrastructure & Standardization

#### 3.1 Logback Configuration
**Action:** Create `logback-spring.xml` in `src/main/resources/` for **ALL** services.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="SERVICE_NAME" source="spring.application.name"/>

    <!-- Console Appender with MDC fields for correlation -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [${SERVICE_NAME}] [%X{traceId:-}] [%X{spanId:-}] [%X{correlationId:-}] [%-5level] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Async appender for performance under load -->
    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="CONSOLE"/>
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <!-- SERVICE-SPECIFIC SETTING:
             - ledger-service: neverBlock=false (audit logs must not be dropped)
             - notification-service: neverBlock=true (acceptable to drop under extreme load)
             - other services: neverBlock=true (default for performance)
        -->
        <neverBlock>true</neverBlock>
    </appender>

    <!-- Root logger -->
    <root level="INFO">
        <appender-ref ref="ASYNC"/>
    </root>

    <!-- Application-specific logging -->
    <logger name="com.patorinaldi.wallet" level="DEBUG"/>
    
    <!-- Reduce noise from frameworks -->
    <logger name="org.springframework.kafka" level="WARN"/>
    <logger name="org.apache.kafka" level="WARN"/>
    <logger name="org.hibernate.SQL" level="WARN"/>
    <logger name="org.springframework.web" level="INFO"/>
</configuration>
```

#### 3.2 MDC Correlation Filter (Common Module)
**File:** `common/src/main/java/com/patorinaldi/wallet/common/logging/CorrelationIdFilter.java`

**Note:** Micrometer tracing with Zipkin already provides `traceId` propagation. This filter adds an additional `correlationId` for cases where you want explicit correlation separate from traces (e.g., for user-facing error IDs). If you prefer to use only Zipkin's traceId, you can skip this filter.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
```

#### 3.3 Kafka MDC Decorator (Common Module)
**File:** `common/src/main/java/com/patorinaldi/wallet/common/logging/KafkaMdcDecorator.java`

**Note:** This is a utility class with static methods only - no `@Component` annotation needed.

```java
public final class KafkaMdcDecorator {
    public static final String TRANSACTION_ID_MDC_KEY = "transactionId";
    public static final String USER_ID_MDC_KEY = "userId";

    private KafkaMdcDecorator() {}

    public static void withMdc(UUID transactionId, UUID userId, Runnable action) {
        try {
            if (transactionId != null) MDC.put(TRANSACTION_ID_MDC_KEY, transactionId.toString());
            if (userId != null) MDC.put(USER_ID_MDC_KEY, userId.toString());
            action.run();
        } finally {
            MDC.remove(TRANSACTION_ID_MDC_KEY);
            MDC.remove(USER_ID_MDC_KEY);
        }
    }
}
```

#### 3.4 Sensitive Data Masking (Common Module)
**File:** `common/src/main/java/com/patorinaldi/wallet/common/logging/LogMasker.java`

```java
public final class LogMasker {
    private LogMasker() {}

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        return (atIndex <= 1) ? "***" + email.substring(atIndex) : email.charAt(0) + "***" + email.substring(atIndex);
    }

    public static String maskUuid(UUID uuid) {
        if (uuid == null) return "***";
        String str = uuid.toString();
        return str.substring(0, 8) + "-***";
    }
}
```

#### 3.5 Update Exception Handlers
**Action:** Update `GlobalExceptionHandler` in all services to log exceptions with appropriate levels (WARN for 4xx, ERROR for 5xx).

#### 3.6 Update Kafka Error Config
**Action:** Update `KafkaErrorConfig` in `common` to log retries and DLT events.

---

## Files to Create/Modify (Observability)

### New Files (4 files)
```
common/src/main/java/com/patorinaldi/wallet/common/logging/
├── CorrelationIdFilter.java
├── KafkaMdcDecorator.java
└── LogMasker.java

prometheus.yml
```

### Files to Modify
```
compose.yaml                                    # Add Prometheus + Grafana
*/src/main/resources/application.yml            # Add management endpoints
*/src/main/resources/logback-spring.xml         # Create/Update logging config
*/src/test/resources/application-test.yml       # Add management endpoints for tests
*/GlobalExceptionHandler.java                   # Add logging
common/.../KafkaErrorConfig.java                # Add logging
*/build.gradle.kts                              # Add micrometer-registry-prometheus
```
