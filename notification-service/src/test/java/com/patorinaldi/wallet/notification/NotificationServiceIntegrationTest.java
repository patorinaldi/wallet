package com.patorinaldi.wallet.notification;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.FraudAlertEvent;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
import com.patorinaldi.wallet.notification.entity.NotificationLog;
import com.patorinaldi.wallet.notification.entity.NotificationType;
import com.patorinaldi.wallet.notification.entity.UserInfo;
import com.patorinaldi.wallet.notification.repository.NotificationLogRepository;
import com.patorinaldi.wallet.notification.repository.UserInfoRepository;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());

        registry.add("spring.kafka.producer.key-serializer", StringSerializer.class::getName);
        registry.add("spring.kafka.producer.value-serializer", JacksonJsonSerializer.class::getName);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
        userInfoRepository.deleteAll();
        greenMail.reset();
    }

    @Test
    void shouldSendWelcomeEmailWhenUserRegisters() {
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .recordId(recordId)
                .userId(userId)
                .email("newuser@example.com")
                .fullName("New User")
                .registeredAt(Instant.now())
                .build();

        kafkaTemplate.send("user-registered", userId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MimeMessage[] messages = greenMail.getReceivedMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages[0].getSubject()).isEqualTo("Welcome to Wallet!");
            assertThat(messages[0].getAllRecipients()[0].toString()).isEqualTo("newuser@example.com");
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<UserInfo> cachedUser = userInfoRepository.findById(userId);
            assertThat(cachedUser).isPresent();
            assertThat(cachedUser.get().getEmail()).isEqualTo("newuser@example.com");
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(notificationLogRepository.existsByEventId(recordId)).isTrue();
        });
    }

    @Test
    void shouldSendWalletCreatedEmail() {
        UUID userId = UUID.randomUUID();
        createAndSaveUser(userId, "user@example.com", "Test User");

        UUID walletId = UUID.randomUUID();
        WalletCreatedEvent event = WalletCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .walletId(walletId)
                .userId(userId)
                .currency("USD")
                .createdAt(Instant.now())
                .build();

        kafkaTemplate.send("wallet-created", userId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MimeMessage[] messages = greenMail.getReceivedMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages[0].getSubject()).contains("New Wallet Created");
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(notificationLogRepository.existsByEventId(walletId)).isTrue();
        });
    }

    @Test
    void shouldSendTransactionReceiptEmail() {
        UUID userId = UUID.randomUUID();
        createAndSaveUser(userId, "user@example.com", "Test User");

        UUID transactionId = UUID.randomUUID();
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(transactionId)
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("500.00"))
                .completedAt(Instant.now())
                .build();

        kafkaTemplate.send("transaction-completed", userId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MimeMessage[] messages = greenMail.getReceivedMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages[0].getSubject()).contains("Transaction Receipt");
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<NotificationLog> log = notificationLogRepository.findByEventId(transactionId);
            assertThat(log).isPresent();
            assertThat(log.get().getNotificationType()).isEqualTo(NotificationType.TRANSACTION);
        });
    }

    @Test
    void shouldSendFraudAlertToAdmin() {
        UUID analysisId = UUID.randomUUID();
        FraudAlertEvent event = FraudAlertEvent.builder()
                .analysisId(analysisId)
                .transactionId(UUID.randomUUID())
                .riskScore(85)
                .decision("BLOCK")
                .build();

        kafkaTemplate.send("fraud-alert", analysisId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MimeMessage[] messages = greenMail.getReceivedMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages[0].getSubject()).contains("Fraud Alert");
            assertThat(messages[0].getAllRecipients()[0].toString()).isEqualTo("admin@wallet.com");
        });
    }

    @Test
    void shouldSendAccountBlockedNotification() {
        UUID userId = UUID.randomUUID();
        createAndSaveUser(userId, "blocked@example.com", "Blocked User");

        UUID triggeredByTransactionId = UUID.randomUUID();
        UserBlockedEvent event = UserBlockedEvent.builder()
                .userId(userId)
                .triggeredByTransactionId(triggeredByTransactionId)
                .reason("Fraud detection: high risk score")
                .riskScore(90)
                .blockedAt(Instant.now())
                .build();

        kafkaTemplate.send("user-blocked", userId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MimeMessage[] messages = greenMail.getReceivedMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages[0].getSubject()).contains("Account Has Been Blocked");
            assertThat(messages[0].getAllRecipients()[0].toString()).isEqualTo("blocked@example.com");
        });
    }

    @Test
    void shouldNotSendDuplicateNotifications() {
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .recordId(recordId)
                .userId(userId)
                .email("duplicate@example.com")
                .fullName("Duplicate Test")
                .registeredAt(Instant.now())
                .build();

        kafkaTemplate.send("user-registered", userId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(greenMail.getReceivedMessages()).hasSize(1);
        });

        kafkaTemplate.send("user-registered", userId.toString(), event);

        // Give Kafka time to process the duplicate and assert the count does not increase
        await().pollDelay(2, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).hasSize(1));
    }

    @Test
    void shouldSkipNotificationWhenUserNotFoundForBlockedEvent() {
        UUID userId = UUID.randomUUID();
        UUID triggeredByTransactionId = UUID.randomUUID();
        UserBlockedEvent event = UserBlockedEvent.builder()
                .userId(userId)
                .triggeredByTransactionId(triggeredByTransactionId)
                .reason("Pre-blocked bad actor")
                .riskScore(100)
                .blockedAt(Instant.now())
                .build();

        kafkaTemplate.send("user-blocked", userId.toString(), event);

        // Wait a short period to ensure the message is consumed and discarded
        await().pollDelay(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(greenMail.getReceivedMessages()).isEmpty();
        });
    }

    private UserInfo createAndSaveUser(UUID userId, String email, String fullName) {
        UserInfo user = UserInfo.builder()
                .userId(userId)
                .email(email)
                .fullName(fullName)
                .createdAt(Instant.now())
                .build();
        return userInfoRepository.save(user);
    }
}
