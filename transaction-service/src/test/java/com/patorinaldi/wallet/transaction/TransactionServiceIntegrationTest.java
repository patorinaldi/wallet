package com.patorinaldi.wallet.transaction;

import com.patorinaldi.wallet.common.enums.TransactionStatus;
import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.transaction.dto.BalanceResponse;
import com.patorinaldi.wallet.transaction.dto.DepositRequest;
import com.patorinaldi.wallet.transaction.dto.ErrorResponse;
import com.patorinaldi.wallet.transaction.dto.TransactionResponse;
import com.patorinaldi.wallet.transaction.dto.TransferRequest;
import com.patorinaldi.wallet.transaction.dto.WithdrawalRequest;
import com.patorinaldi.wallet.transaction.entity.Transaction;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.helper.TestDataBuilder;
import com.patorinaldi.wallet.transaction.repository.TransactionRepository;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransactionServiceIntegrationTest {

    @LocalServerPort
    private int port;

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
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    private RestTestClient restTestClient;

    @BeforeEach
    public void setup() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        // Clean database before each test
        transactionRepository.deleteAll();
        walletBalanceRepository.deleteAll();
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

    // ========== DEPOSIT TESTS ==========

    @Test
    void shouldDepositSuccessfully() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
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
        assertEquals(TransactionType.DEPOSIT, response.type());
        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(new BigDecimal("50.00"), response.amount());
        assertEquals(new BigDecimal("150.0000"), response.balanceAfter());

        // Verify in database
        Optional<Transaction> txInDb = transactionRepository.findAll().stream().findFirst();
        assertTrue(txInDb.isPresent());
        assertEquals(TransactionStatus.COMPLETED, txInDb.get().getStatus());

        WalletBalance updatedWallet = walletBalanceRepository.findByWalletId(wallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("150.0000"), updatedWallet.getBalance());
    }

    @Test
    void shouldRejectDepositForNonExistentWallet() {
        // Given
        UUID nonExistentWalletId = UUID.randomUUID();
        DepositRequest request = TestDataBuilder.createDepositRequest(
                nonExistentWalletId,
                new BigDecimal("50.00"),
                "Test deposit"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(404, errorResponse.status());
        assertTrue(errorResponse.message().contains("Wallet balance not found"));

        // Verify no transaction created
        assertEquals(0, transactionRepository.findAll().size());
    }

    @Test
    void shouldRejectDuplicateDeposit() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        String idempotencyKey = "TEST-DEPOSIT-001";
        DepositRequest request = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("50.00"),
                "Test deposit",
                idempotencyKey
        );

        // First deposit
        restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isCreated();

        // When - try duplicate
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(409, errorResponse.status());
        assertTrue(errorResponse.message().contains("Transaction already exists"));

        // Verify only one transaction exists
        assertEquals(1, transactionRepository.findAll().size());

        // Verify balance only updated once
        WalletBalance updatedWallet = walletBalanceRepository.findByWalletId(wallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("150.0000"), updatedWallet.getBalance());
    }

    // ========== WITHDRAWAL TESTS ==========

    @Test
    void shouldWithdrawSuccessfully() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(
                wallet.getWalletId(),
                new BigDecimal("30.00"),
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
        assertEquals(TransactionType.WITHDRAWAL, response.type());
        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(new BigDecimal("30.00"), response.amount());
        assertEquals(new BigDecimal("70.0000"), response.balanceAfter());

        // Verify in database
        WalletBalance updatedWallet = walletBalanceRepository.findByWalletId(wallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("70.0000"), updatedWallet.getBalance());
    }

    @Test
    void shouldRejectWithdrawalWithInsufficientBalance() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("50.00"));
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(
                wallet.getWalletId(),
                new BigDecimal("100.00"),
                "Test withdrawal"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(400, errorResponse.status());
        assertTrue(errorResponse.message().contains("Insufficient balance"));

        // Verify failed transaction was saved
        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        assertEquals(TransactionStatus.FAILED, transactions.get(0).getStatus());
        assertNotNull(transactions.get(0).getErrorMessage());

        // Verify balance unchanged
        WalletBalance unchangedWallet = walletBalanceRepository.findByWalletId(wallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("50.0000"), unchangedWallet.getBalance());
    }

    @Test
    void shouldRejectWithdrawalForNonExistentWallet() {
        // Given
        UUID nonExistentWalletId = UUID.randomUUID();
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(
                nonExistentWalletId,
                new BigDecimal("30.00"),
                "Test withdrawal"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(404, errorResponse.status());
        assertTrue(errorResponse.message().contains("Wallet balance not found"));

        // Verify no transaction created
        assertEquals(0, transactionRepository.findAll().size());
    }

    @Test
    void shouldRejectDuplicateWithdrawal() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        String idempotencyKey = "TEST-WITHDRAWAL-001";
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(
                wallet.getWalletId(),
                new BigDecimal("30.00"),
                "Test withdrawal",
                idempotencyKey
        );

        // First withdrawal
        restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(request)
                .exchange()
                .expectStatus().isCreated();

        // When - try duplicate
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(409, errorResponse.status());

        // Verify only one transaction exists
        assertEquals(1, transactionRepository.findAll().size());
    }

    // ========== TRANSFER TESTS ==========

    @Test
    void shouldTransferSuccessfully() {
        // Given
        WalletBalance sourceWallet = setupWalletWithBalance(new BigDecimal("200.00"));
        WalletBalance destWallet = setupWalletWithBalance(new BigDecimal("50.00"));

        TransferRequest request = TestDataBuilder.createTransferRequest(
                sourceWallet.getWalletId(),
                destWallet.getWalletId(),
                new BigDecimal("75.00"),
                "Test transfer"
        );

        // When
        TransactionResponse response = restTestClient.post()
                .uri("/api/transactions/transfer")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(response);
        assertEquals(TransactionType.TRANSFER_OUT, response.type());
        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(new BigDecimal("75.00"), response.amount());
        assertEquals(new BigDecimal("125.0000"), response.balanceAfter());
        assertEquals(destWallet.getWalletId(), response.relatedWalletId());
        assertNotNull(response.relatedTransactionId());

        // Verify two transactions created
        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(2, transactions.size());

        Transaction transferOut = transactions.stream()
                .filter(tx -> tx.getType() == TransactionType.TRANSFER_OUT)
                .findFirst()
                .orElseThrow();

        Transaction transferIn = transactions.stream()
                .filter(tx -> tx.getType() == TransactionType.TRANSFER_IN)
                .findFirst()
                .orElseThrow();

        // Verify linking
        assertEquals(destWallet.getWalletId(), transferOut.getRelatedWalletId());
        assertEquals(transferIn.getId(), transferOut.getRelatedTransactionId());
        assertEquals(sourceWallet.getWalletId(), transferIn.getRelatedWalletId());
        assertEquals(transferOut.getId(), transferIn.getRelatedTransactionId());

        // Verify balances
        WalletBalance updatedSource = walletBalanceRepository.findByWalletId(sourceWallet.getWalletId()).orElseThrow();
        WalletBalance updatedDest = walletBalanceRepository.findByWalletId(destWallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("125.0000"), updatedSource.getBalance());
        assertEquals(new BigDecimal("125.0000"), updatedDest.getBalance());
    }

    @Test
    void shouldRejectTransferWithInsufficientBalance() {
        // Given
        WalletBalance sourceWallet = setupWalletWithBalance(new BigDecimal("50.00"));
        WalletBalance destWallet = setupWalletWithBalance(new BigDecimal("100.00"));

        TransferRequest request = TestDataBuilder.createTransferRequest(
                sourceWallet.getWalletId(),
                destWallet.getWalletId(),
                new BigDecimal("100.00"),
                "Test transfer"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/transfer")
                .body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(400, errorResponse.status());
        assertTrue(errorResponse.message().contains("Insufficient balance"));

        // Verify only TRANSFER_OUT failed transaction saved
        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        assertEquals(TransactionType.TRANSFER_OUT, transactions.get(0).getType());
        assertEquals(TransactionStatus.FAILED, transactions.get(0).getStatus());

        // Verify balances unchanged
        WalletBalance unchangedSource = walletBalanceRepository.findByWalletId(sourceWallet.getWalletId()).orElseThrow();
        WalletBalance unchangedDest = walletBalanceRepository.findByWalletId(destWallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("50.0000"), unchangedSource.getBalance());
        assertEquals(new BigDecimal("100.0000"), unchangedDest.getBalance());
    }

    @Test
    void shouldRejectTransferForNonExistentSourceWallet() {
        // Given
        UUID nonExistentWalletId = UUID.randomUUID();
        WalletBalance destWallet = setupWalletWithBalance(new BigDecimal("100.00"));

        TransferRequest request = TestDataBuilder.createTransferRequest(
                nonExistentWalletId,
                destWallet.getWalletId(),
                new BigDecimal("50.00"),
                "Test transfer"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/transfer")
                .body(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(404, errorResponse.status());
        assertTrue(errorResponse.message().contains("Wallet balance not found"));

        // Verify no transactions created
        assertEquals(0, transactionRepository.findAll().size());
    }

    @Test
    void shouldRejectTransferForNonExistentDestinationWallet() {
        // Given
        WalletBalance sourceWallet = setupWalletWithBalance(new BigDecimal("200.00"));
        UUID nonExistentWalletId = UUID.randomUUID();

        TransferRequest request = TestDataBuilder.createTransferRequest(
                sourceWallet.getWalletId(),
                nonExistentWalletId,
                new BigDecimal("50.00"),
                "Test transfer"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/transfer")
                .body(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(404, errorResponse.status());
        assertTrue(errorResponse.message().contains("Wallet balance not found"));

        // Verify no transactions created
        assertEquals(0, transactionRepository.findAll().size());
    }

    @Test
    void shouldRejectDuplicateTransfer() {
        // Given
        WalletBalance sourceWallet = setupWalletWithBalance(new BigDecimal("200.00"));
        WalletBalance destWallet = setupWalletWithBalance(new BigDecimal("50.00"));
        String idempotencyKey = "TEST-TRANSFER-001";

        TransferRequest request = TestDataBuilder.createTransferRequest(
                sourceWallet.getWalletId(),
                destWallet.getWalletId(),
                new BigDecimal("75.00"),
                "Test transfer",
                idempotencyKey
        );

        // First transfer
        restTestClient.post()
                .uri("/api/transactions/transfer")
                .body(request)
                .exchange()
                .expectStatus().isCreated();

        // When - try duplicate
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/transfer")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(409, errorResponse.status());

        // Verify only original two transactions exist (OUT and IN)
        assertEquals(2, transactionRepository.findAll().size());
    }

    // ========== EDGE CASES ==========

    @Test
    void shouldHandleValidationErrors() {
        // Given - invalid request with null amount
        DepositRequest invalidRequest = new DepositRequest(
                UUID.randomUUID(),
                null, // null amount should fail validation
                "Test",
                "KEY-001"
        );

        // When
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(400, errorResponse.status());
    }

    @Test
    void shouldPersistFailedTransactionDespiteRollback() {
        // Given
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("50.00"));
        WithdrawalRequest request = TestDataBuilder.createWithdrawalRequest(
                wallet.getWalletId(),
                new BigDecimal("100.00"),
                "Test insufficient balance"
        );

        // When - withdrawal fails
        restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(request)
                .exchange()
                .expectStatus().isBadRequest();

        // Then - failed transaction should be persisted (REQUIRES_NEW)
        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        assertEquals(TransactionStatus.FAILED, transactions.get(0).getStatus());
        assertNotNull(transactions.get(0).getErrorMessage());

        // Wallet balance should be unchanged
        WalletBalance unchangedWallet = walletBalanceRepository.findByWalletId(wallet.getWalletId()).orElseThrow();
        assertEquals(new BigDecimal("50.0000"), unchangedWallet.getBalance());
    }

    // ========== QUERY ENDPOINT TESTS ==========

    @Test
    void shouldGetTransactionsByWalletId() {
        // Given - wallet with multiple transactions
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("200.00"));

        DepositRequest depositRequest = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("100.00"),
                "Deposit 1"
        );
        restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(depositRequest)
                .exchange()
                .expectStatus().isCreated();

        WithdrawalRequest withdrawalRequest = TestDataBuilder.createWithdrawalRequest(
                wallet.getWalletId(),
                new BigDecimal("50.00"),
                "Withdrawal 1"
        );
        restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(withdrawalRequest)
                .exchange()
                .expectStatus().isCreated();

        // When - get transactions for wallet
        String response = restTestClient.get()
                .uri("/api/transactions?walletId=" + wallet.getWalletId() + "&page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(response);
        assertTrue(response.contains("\"totalElements\":2"));
        assertTrue(response.contains("DEPOSIT"));
        assertTrue(response.contains("WITHDRAWAL"));
    }

    @Test
    void shouldGetEmptyPageForWalletWithNoTransactions() {
        // Given - wallet with no transactions
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));

        // When
        String response = restTestClient.get()
                .uri("/api/transactions?walletId=" + wallet.getWalletId() + "&page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(response);
        assertTrue(response.contains("\"totalElements\":0"));
        assertTrue(response.contains("\"content\":[]"));
    }

    @Test
    void shouldGetTransactionById() {
        // Given - create a transaction
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("100.00"));
        DepositRequest request = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("50.00"),
                "Test deposit"
        );

        TransactionResponse createdTransaction = restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(createdTransaction);
        UUID transactionId = createdTransaction.id();

        // When - get transaction by ID
        TransactionResponse response = restTestClient.get()
                .uri("/api/transactions/" + transactionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(response);
        assertEquals(transactionId, response.id());
        assertEquals(TransactionType.DEPOSIT, response.type());
        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(0, response.amount().compareTo(new BigDecimal("50.00")));
    }

    @Test
    void shouldReturn404ForNonExistentTransaction() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        ErrorResponse errorResponse = restTestClient.get()
                .uri("/api/transactions/" + nonExistentId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(404, errorResponse.status());
        assertTrue(errorResponse.message().contains("Transaction not found"));
    }

    @Test
    void shouldGetBalance() {
        // Given - wallet with initial balance and some transactions
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("200.00"));

        DepositRequest depositRequest = TestDataBuilder.createDepositRequest(
                wallet.getWalletId(),
                new BigDecimal("100.00"),
                "Deposit"
        );
        restTestClient.post()
                .uri("/api/transactions/deposit")
                .body(depositRequest)
                .exchange()
                .expectStatus().isCreated();

        WithdrawalRequest withdrawalRequest = TestDataBuilder.createWithdrawalRequest(
                wallet.getWalletId(),
                new BigDecimal("50.00"),
                "Withdrawal"
        );
        restTestClient.post()
                .uri("/api/transactions/withdrawal")
                .body(withdrawalRequest)
                .exchange()
                .expectStatus().isCreated();

        // When - get balance
        BalanceResponse response = restTestClient.get()
                .uri("/api/transactions/balances/" + wallet.getWalletId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BalanceResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(response);
        assertEquals(wallet.getWalletId(), response.walletId());
        assertEquals(wallet.getUserId(), response.userId());
        assertEquals(0, response.balance().compareTo(new BigDecimal("250.00"))); // 200 + 100 - 50 = 250
        assertEquals("USD", response.currency());
        assertNotNull(response.lastUpdated());
    }

    @Test
    void shouldReturn404ForNonExistentWalletBalance() {
        // Given
        UUID nonExistentWalletId = UUID.randomUUID();

        // When
        ErrorResponse errorResponse = restTestClient.get()
                .uri("/api/transactions/balances/" + nonExistentWalletId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Then
        assertNotNull(errorResponse);
        assertEquals(404, errorResponse.status());
        assertTrue(errorResponse.message().contains("Wallet balance not found"));
    }

    @Test
    void shouldSupportPaginationForTransactions() {
        // Given - wallet with many transactions
        WalletBalance wallet = setupWalletWithBalance(new BigDecimal("500.00"));

        // Create 5 transactions
        for (int i = 0; i < 5; i++) {
            DepositRequest request = TestDataBuilder.createDepositRequest(
                    wallet.getWalletId(),
                    new BigDecimal("10.00"),
                    "Deposit " + i
            );
            restTestClient.post()
                    .uri("/api/transactions/deposit")
                    .body(request)
                    .exchange()
                    .expectStatus().isCreated();
        }

        // When - get first page with size 2
        String firstPage = restTestClient.get()
                .uri("/api/transactions?walletId=" + wallet.getWalletId() + "&page=0&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Then - verify pagination
        assertNotNull(firstPage);
        assertTrue(firstPage.contains("\"totalElements\":5"));
        assertTrue(firstPage.contains("\"totalPages\":3"));
        assertTrue(firstPage.contains("\"size\":2"));
        assertTrue(firstPage.contains("\"number\":0")); // page number
    }
}
