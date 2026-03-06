package com.patorinaldi.wallet.transaction;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.patorinaldi.wallet.transaction.dto.DepositRequest;
import com.patorinaldi.wallet.transaction.dto.ErrorResponse;
import com.patorinaldi.wallet.transaction.dto.TransactionResponse;
import com.patorinaldi.wallet.transaction.dto.WithdrawalRequest;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.helper.TestDataBuilder;
import com.patorinaldi.wallet.transaction.repository.BlockedUserRepository;
import com.patorinaldi.wallet.transaction.repository.TransactionRepository;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FraudCheckIntegrationTest {

    @LocalServerPort
    private int port;

    private static WireMockServer wireMockServer;

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
        registry.add("fraud.service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("fraud.service.timeout-ms", () -> "5000");
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private BlockedUserRepository blockedUserRepository;

    private RestTestClient restTestClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        transactionRepository.deleteAll();
        walletBalanceRepository.deleteAll();
        blockedUserRepository.deleteAll();
        wireMockServer.resetAll();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetAll();
    }

    // ========== HELPER METHODS ==========

    private WalletBalance setupWalletWithBalance(BigDecimal balance) {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WalletBalance walletBalance = TestDataBuilder.createWalletBalance(
                walletId, userId, balance, "USD"
        );
        return walletBalanceRepository.save(walletBalance);
    }

    private void stubFraudServiceApprove() {
        wireMockServer.stubFor(post(urlEqualTo("/api/fraud/check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                    "riskScore": 0,
                                    "decision": "APPROVE",
                                    "triggeredRules": [],
                                    "message": "Transaction approved"
                                }
                                """)));
    }

    private void stubFraudServiceFlag() {
        wireMockServer.stubFor(post(urlEqualTo("/api/fraud/check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                    "riskScore": 55,
                                    "decision": "FLAG",
                                    "triggeredRules": ["LARGE_AMOUNT"],
                                    "message": "Transaction flagged for review"
                                }
                                """)));
    }

    private void stubFraudServiceBlock() {
        wireMockServer.stubFor(post(urlEqualTo("/api/fraud/check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                    "riskScore": 85,
                                    "decision": "BLOCK",
                                    "triggeredRules": ["VERY_LARGE_AMOUNT", "HIGH_VELOCITY"],
                                    "message": "Transaction blocked due to high risk"
                                }
                                """)));
    }

    private void stubFraudServiceTimeout() {
        wireMockServer.stubFor(post(urlEqualTo("/api/fraud/check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(10000))); // 10 second delay to trigger timeout
    }

    private void stubFraudServiceError() {
        wireMockServer.stubFor(post(urlEqualTo("/api/fraud/check"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));
    }

    // ========== FRAUD CHECK APPROVE TESTS ==========

    @Test
    void shouldApproveDepositWhenFraudServiceApproves() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        stubFraudServiceApprove();

        DepositRequest request = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("50.00"),
                "Test deposit"
        );

        // When
        TransactionResponse response = restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(response);
        assertEquals(new BigDecimal("50.00"), response.amount());
        assertEquals(new BigDecimal("150.0000"), response.balanceAfter());

        // Verify fraud service was called
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/fraud/check")));
    }

    @Test
    void shouldApproveWithdrawalWhenFraudServiceApproves() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("200.00"));
        stubFraudServiceApprove();

        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(
                wallet.getWalletId(),
                new BigDecimal("50.00"),
                "Test withdrawal"
        );

        // When
        TransactionResponse response = restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(response);
        assertEquals(new BigDecimal("50.00"), response.amount());
        assertEquals(new BigDecimal("150.0000"), response.balanceAfter());

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/fraud/check")));
    }

    // ========== FRAUD CHECK FLAG TESTS ==========

    @Test
    void shouldProceedWithDepositWhenFraudServiceFlags() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        stubFraudServiceFlag();

        DepositRequest request = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("15000.00"),
                "Large deposit - flagged"
        );

        // When - FLAG should still allow transaction to proceed
        TransactionResponse response = restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(response);
        assertEquals(new BigDecimal("15000.00"), response.amount());
        assertEquals(new BigDecimal("15100.0000"), response.balanceAfter());

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/fraud/check")));
    }

    // ========== FRAUD CHECK BLOCK TESTS ==========

    @Test
    void shouldBlockDepositWhenFraudServiceBlocks() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        stubFraudServiceBlock();

        DepositRequest request = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("60000.00"),
                "Very large deposit - should be blocked"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(403, errorResponse.status());
        assertTrue(errorResponse.message().contains("blocked by fraud detection"));

        // Verify balance unchanged
        WalletBalance unchangedWallet = walletBalanceRepository.findByWalletId(wallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("100.0000"), unchangedWallet.getBalance());

        // Verify no transaction was saved
        assertEquals(0, transactionRepository.findAll().size());

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/fraud/check")));
    }

    @Test
    void shouldBlockWithdrawalWhenFraudServiceBlocks() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100000.00"));
        stubFraudServiceBlock();

        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(
                wallet.getWalletId(),
                new BigDecimal("60000.00"),
                "Very large withdrawal - should be blocked"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(403, errorResponse.status());
        assertTrue(errorResponse.message().contains("blocked by fraud detection"));

        // Verify balance unchanged
        WalletBalance unchangedWallet = walletBalanceRepository.findByWalletId(wallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("100000.0000"), unchangedWallet.getBalance());

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/fraud/check")));
    }

    // ========== CIRCUIT BREAKER FALLBACK TESTS ==========

    @Test
    void shouldProceedWithFlagWhenFraudServiceIsUnavailable() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        stubFraudServiceError(); // 500 error to trigger circuit breaker fallback

        DepositRequest request = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("50.00"),
                "Deposit when fraud service is down"
        );

        // When - Circuit breaker fallback should allow transaction with FLAG
        TransactionResponse response = restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();

        // Then - Transaction should proceed (fail-open with FLAG)
        assertNotNull(response);
        assertEquals(new BigDecimal("50.00"), response.amount());
        assertEquals(new BigDecimal("150.0000"), response.balanceAfter());
    }

    @Test
    void shouldProceedWithMultipleTransactionsWhenCircuitBreakerIsTriggered() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("500.00"));
        stubFraudServiceError(); // Will trigger circuit breaker

        // When - Execute multiple transactions to trigger circuit breaker
        for (int i = 0; i < 3; i++) {
            DepositRequest request = TestDataBuilder.createDepositRequest(
                    wallet.getWalletId(),
                    new BigDecimal("10.00"),
                    "Deposit " + i
            );

            // All transactions should proceed with fallback
            restTestClient.post()
                    .uri("/api/transactions/deposit")
                    .body(request)
                    .exchange()
                    .expectStatus().isCreated();
        }

        // Then - All transactions should have been processed
        WalletBalance updatedWallet = walletBalanceRepository.findByWalletId(wallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("530.0000"), updatedWallet.getBalance()); // 500 + 3 * 10
    }

    // ========== FRAUD CHECK REQUEST VERIFICATION ==========

    @Test
    void shouldSendCorrectFraudCheckRequest() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        stubFraudServiceApprove();

        DepositRequest request = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("250.00"),
                "Test request verification"
        );

        // When
        restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isCreated();

        // Then - Verify the request sent to fraud service
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/fraud/check"))
                .withRequestBody(matchingJsonPath("$.walletId", equalTo(wallet.getWalletId().toString())))
                .withRequestBody(matchingJsonPath("$.userId", equalTo(wallet.getUserId().toString())))
                .withRequestBody(matchingJsonPath("$.amount"))
                .withRequestBody(matchingJsonPath("$.transactionType", equalTo("DEPOSIT")))
                .withRequestBody(matchingJsonPath("$.currency", equalTo("USD"))));
    }
}
