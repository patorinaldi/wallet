package com.patorinaldi.wallet.transaction.event;

import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.helper.TestDataBuilder;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class WalletEventListenerIntegrationTest {

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
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private KafkaTemplate<String, WalletCreatedEvent> kafkaTemplate;

    @BeforeEach
    void setup() {
        walletBalanceRepository.deleteAll();
    }

    @Test
    void shouldCreateWalletBalance_whenWalletCreatedEventPublished() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String currency = "EUR";
        WalletCreatedEvent event = TestDataBuilder.createWalletCreatedEvent(walletId, userId, currency);

        // When
        kafkaTemplate.send("wallet-created", walletId.toString(), event);

        // Then
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Optional<WalletBalance> balance = walletBalanceRepository.findByWalletId(walletId);
                    assertTrue(balance.isPresent());
                    assertEquals(walletId, balance.get().getWalletId());
                    assertEquals(userId, balance.get().getUserId());
                    assertEquals(currency, balance.get().getCurrency());
                    assertEquals(0, balance.get().getBalance().compareTo(BigDecimal.ZERO));
                });
    }

    @Test
    void shouldNotCreateDuplicateBalance_whenDuplicateEventPublished() {
        // Given
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String currency = "USD";
        WalletCreatedEvent event = TestDataBuilder.createWalletCreatedEvent(walletId, userId, currency);

        // When - publish same event twice
        kafkaTemplate.send("wallet-created", walletId.toString(), event);
        kafkaTemplate.send("wallet-created", walletId.toString(), event);

        // Then - wait for events to be processed
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Optional<WalletBalance> balance = walletBalanceRepository.findByWalletId(walletId);
                    assertTrue(balance.isPresent());
                });

        // Give some time for potential duplicate processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify only one balance exists
        List<WalletBalance> allBalances = walletBalanceRepository.findAll();
        long countForWallet = allBalances.stream()
                .filter(b -> b.getWalletId().equals(walletId))
                .count();
        assertEquals(1, countForWallet);
    }

    @Test
    void shouldHandleMultipleEvents_concurrently() {
        // Given - create 5 different wallet events
        WalletCreatedEvent event1 = TestDataBuilder.createWalletCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "USD");
        WalletCreatedEvent event2 = TestDataBuilder.createWalletCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "EUR");
        WalletCreatedEvent event3 = TestDataBuilder.createWalletCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "GBP");
        WalletCreatedEvent event4 = TestDataBuilder.createWalletCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "USD");
        WalletCreatedEvent event5 = TestDataBuilder.createWalletCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "EUR");

        // When - publish all events
        kafkaTemplate.send("wallet-created", event1.walletId().toString(), event1);
        kafkaTemplate.send("wallet-created", event2.walletId().toString(), event2);
        kafkaTemplate.send("wallet-created", event3.walletId().toString(), event3);
        kafkaTemplate.send("wallet-created", event4.walletId().toString(), event4);
        kafkaTemplate.send("wallet-created", event5.walletId().toString(), event5);

        // Then - all balances should be created
        await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<WalletBalance> allBalances = walletBalanceRepository.findAll();
                    assertEquals(5, allBalances.size());

                    // Verify all have correct initial values
                    allBalances.forEach(balance -> {
                        assertEquals(0, balance.getBalance().compareTo(BigDecimal.ZERO));
                        assertNotNull(balance.getWalletId());
                        assertNotNull(balance.getUserId());
                        assertNotNull(balance.getCurrency());
                    });
                });
    }
}
