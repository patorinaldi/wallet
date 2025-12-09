package com.patorinaldi.wallet.account;

import com.patorinaldi.wallet.account.entity.User;
import com.patorinaldi.wallet.account.entity.UserStatus;
import com.patorinaldi.wallet.account.repository.UserBlockLogRepository;
import com.patorinaldi.wallet.account.repository.UserRepository;
import com.patorinaldi.wallet.common.event.UserBlockedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserBlockedEventListenerIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private UserBlockLogRepository userBlockLogRepository;

    @BeforeEach
    public void setup() {
        userBlockLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldBlockUserWhenEventIsConsumed() {
        // Given
        User userToBlock = User.builder()
                .email("test.user@example.com")
                .fullName("Test User")
                .phoneNumber("+1234567890")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(userToBlock);

        UserBlockedEvent event = new UserBlockedEvent(
                userToBlock.getId(),
                UUID.randomUUID(),
                "High risk score",
                90,
                Instant.now()
        );

        // When
        kafkaTemplate.send("user-blocked", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            User userInDb = userRepository.findById(userToBlock.getId()).orElseThrow();
            assert userInDb.getStatus() == UserStatus.BLOCKED;
            assert userInDb.getBlockReason().equals("High risk score");
            assert userInDb.getBlockedByTransactionId().equals(event.triggeredByTransactionId());

            long logCount = userBlockLogRepository.count();
            assert logCount == 1;
        });
    }

    @Test
    void shouldProcessBlockEventOnlyOnceWhenSentTwice() {
        // Given
        User userToBlock = User.builder()
                .email("idempotent.user@example.com")
                .fullName("Idempotent User")
                .phoneNumber("+1987654321")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(userToBlock);

        UserBlockedEvent event = new UserBlockedEvent(
                userToBlock.getId(),
                UUID.randomUUID(),
                "Idempotency test",
                85,
                Instant.now()
        );

        // When
        kafkaTemplate.send("user-blocked", event.userId().toString(), event);
        kafkaTemplate.send("user-blocked", event.userId().toString(), event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            User userInDb = userRepository.findById(userToBlock.getId()).orElseThrow();
            assert userInDb.getStatus() == UserStatus.BLOCKED;

            long logCount = userBlockLogRepository.count();
            assert logCount == 1;
        });
    }
}
