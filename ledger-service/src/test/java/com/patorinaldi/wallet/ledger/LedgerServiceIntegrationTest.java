package com.patorinaldi.wallet.ledger;

import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.ledger.entity.*;
import com.patorinaldi.wallet.ledger.repository.LedgerAccountRepository;
import com.patorinaldi.wallet.ledger.repository.LedgerEntryRepository;
import com.patorinaldi.wallet.ledger.repository.LedgerJournalRepository;
import com.patorinaldi.wallet.ledger.service.LedgerService;
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

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
public class LedgerServiceIntegrationTest {

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
    private LedgerService ledgerService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerJournalRepository ledgerJournalRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private KafkaTemplate<String, TransactionCompletedEvent> kafkaTemplate;

    @BeforeEach
    void setup() {
        ledgerEntryRepository.deleteAll();
        ledgerJournalRepository.deleteAll();
        // Keep system accounts, only delete user accounts
        ledgerAccountRepository.findAll().stream()
                .filter(acc -> acc.getAccountType() == AccountType.USER_WALLET)
                .forEach(ledgerAccountRepository::delete);
    }

    // ========== SYSTEM ACCOUNT SEEDER TESTS ==========

    @Test
    void shouldCreateSystemAccountsOnStartup() {
        // Then - system accounts should exist after startup
        Optional<LedgerAccount> bankAccount = ledgerAccountRepository
                .findByAccountTypeAndCurrency(AccountType.SYSTEM_BANK, "USD");
        assertTrue(bankAccount.isPresent());
        assertEquals("SYSTEM_BANK-USD", bankAccount.get().getAccountNumber());

        Optional<LedgerAccount> feesAccount = ledgerAccountRepository
                .findByAccountTypeAndCurrency(AccountType.SYSTEM_FEES, "USD");
        assertTrue(feesAccount.isPresent());

        Optional<LedgerAccount> suspenseAccount = ledgerAccountRepository
                .findByAccountTypeAndCurrency(AccountType.SYSTEM_SUSPENSE, "USD");
        assertTrue(suspenseAccount.isPresent());
    }

    // ========== DEPOSIT TESTS ==========

    @Test
    @Transactional
    void shouldProcessDepositAndCreateLedgerEntries() {
        // Given
        TransactionCompletedEvent event = createDepositEvent();

        // When
        ledgerService.processTransaction(event);

        // Then - journal should be created
        Optional<LedgerJournal> journal = ledgerJournalRepository.findByTransactionId(event.transactionId());
        assertTrue(journal.isPresent());
        assertEquals(event.transactionId(), journal.get().getTransactionId());
        assertTrue(journal.get().getDescription().contains("DEPOSIT"));

        // Then - two entries should be created
        List<LedgerEntry> entries = ledgerEntryRepository.findByJournal_Id(journal.get().getId());
        assertEquals(2, entries.size());

        // Verify DEBIT entry (user wallet)
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.DEBIT)
                .findFirst()
                .orElseThrow();
        assertEquals(event.amount(), debitEntry.getAmount());
        assertEquals(event.currency(), debitEntry.getCurrency());
        assertEquals(event.balanceAfter(), debitEntry.getReportedBalanceAfter());
        assertEquals(AccountType.USER_WALLET, debitEntry.getAccount().getAccountType());
        assertEquals(event.walletId(), debitEntry.getAccount().getExternalId());

        // Verify CREDIT entry (system bank)
        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.CREDIT)
                .findFirst()
                .orElseThrow();
        assertEquals(event.amount(), creditEntry.getAmount());
        assertEquals(AccountType.SYSTEM_BANK, creditEntry.getAccount().getAccountType());
    }

    @Test
    void shouldCreateUserAccountOnFirstDeposit() {
        // Given
        UUID walletId = UUID.randomUUID();
        TransactionCompletedEvent event = createDepositEventForWallet(walletId);

        // Verify user account doesn't exist yet
        assertTrue(ledgerAccountRepository.findByExternalIdAndCurrency(walletId, "USD").isEmpty());

        // When
        ledgerService.processTransaction(event);

        // Then - user account should be created
        Optional<LedgerAccount> userAccount = ledgerAccountRepository
                .findByExternalIdAndCurrency(walletId, "USD");
        assertTrue(userAccount.isPresent());
        assertEquals(AccountType.USER_WALLET, userAccount.get().getAccountType());
        assertEquals(walletId, userAccount.get().getExternalId());
        assertTrue(userAccount.get().getAccountNumber().contains(walletId.toString()));
    }

    // ========== WITHDRAWAL TESTS ==========

    @Test
    @Transactional
    void shouldProcessWithdrawalAndCreateLedgerEntries() {
        // Given
        TransactionCompletedEvent event = createWithdrawalEvent();

        // When
        ledgerService.processTransaction(event);

        // Then - journal should be created
        Optional<LedgerJournal> journal = ledgerJournalRepository.findByTransactionId(event.transactionId());
        assertTrue(journal.isPresent());

        // Then - two entries should be created with correct sides
        List<LedgerEntry> entries = ledgerEntryRepository.findByJournal_Id(journal.get().getId());
        assertEquals(2, entries.size());

        // Verify CREDIT entry (user wallet - money leaving)
        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.CREDIT)
                .findFirst()
                .orElseThrow();
        assertEquals(AccountType.USER_WALLET, creditEntry.getAccount().getAccountType());
        assertEquals(event.amount(), creditEntry.getAmount());

        // Verify DEBIT entry (system bank - money going out)
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.DEBIT)
                .findFirst()
                .orElseThrow();
        assertEquals(AccountType.SYSTEM_BANK, debitEntry.getAccount().getAccountType());
        assertEquals(event.amount(), debitEntry.getAmount());
    }

    // ========== TRANSFER TESTS ==========

    @Test
    @Transactional
    void shouldProcessTransferInAndCreateLedgerEntries() {
        // Given
        UUID receiverWalletId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        TransactionCompletedEvent event = createTransferInEvent(receiverWalletId, senderWalletId);

        // When
        ledgerService.processTransaction(event);

        // Then - journal should be created
        Optional<LedgerJournal> journal = ledgerJournalRepository.findByTransactionId(event.transactionId());
        assertTrue(journal.isPresent());

        // Then - two entries should be created
        List<LedgerEntry> entries = ledgerEntryRepository.findByJournal_Id(journal.get().getId());
        assertEquals(2, entries.size());

        // Verify DEBIT entry (receiver wallet - money coming in)
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.DEBIT)
                .findFirst()
                .orElseThrow();
        assertEquals(receiverWalletId, debitEntry.getAccount().getExternalId());
        assertEquals(event.amount(), debitEntry.getAmount());

        // Verify CREDIT entry (sender wallet - money going out)
        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.CREDIT)
                .findFirst()
                .orElseThrow();
        assertEquals(senderWalletId, creditEntry.getAccount().getExternalId());
        assertEquals(event.amount(), creditEntry.getAmount());
    }

    @Test
    void shouldSkipTransferOutAndNotCreateEntries() {
        // Given
        TransactionCompletedEvent event = createTransferOutEvent();

        // When
        ledgerService.processTransaction(event);

        // Then - journal should be created
        Optional<LedgerJournal> journal = ledgerJournalRepository.findByTransactionId(event.transactionId());
        assertTrue(journal.isPresent());

        // Then - NO entries should be created for TRANSFER_OUT
        List<LedgerEntry> entries = ledgerEntryRepository.findByJournal_Id(journal.get().getId());
        assertEquals(0, entries.size());
    }

    // ========== IDEMPOTENCY TESTS ==========

    @Test
    void shouldNotDuplicateEntriesForSameTransaction() {
        // Given
        TransactionCompletedEvent event = createDepositEvent();

        // When - process same event twice
        ledgerService.processTransaction(event);
        ledgerService.processTransaction(event);

        // Then - only one journal should exist
        List<LedgerJournal> journals = ledgerJournalRepository.findAll().stream()
                .filter(j -> j.getTransactionId().equals(event.transactionId()))
                .toList();
        assertEquals(1, journals.size());

        // Then - only two entries should exist (not four)
        List<LedgerEntry> entries = ledgerEntryRepository.findByJournal_Id(journals.get(0).getId());
        assertEquals(2, entries.size());
    }

    // ========== KAFKA INTEGRATION TESTS ==========

    @Test
    void shouldProcessDepositEventFromKafka() {
        // Given
        TransactionCompletedEvent event = createDepositEvent();

        // When - send event to Kafka
        kafkaTemplate.send("transaction-completed", event.walletId().toString(), event);

        // Then - wait for processing and verify
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<LedgerJournal> journal = ledgerJournalRepository.findByTransactionId(event.transactionId());
            assertTrue(journal.isPresent());

            List<LedgerEntry> entries = ledgerEntryRepository.findByJournal_Id(journal.get().getId());
            assertEquals(2, entries.size());
        });
    }

    @Test
    void shouldProcessWithdrawalEventFromKafka() {
        // Given
        TransactionCompletedEvent event = createWithdrawalEvent();

        // When - send event to Kafka
        kafkaTemplate.send("transaction-completed", event.walletId().toString(), event);

        // Then - wait for processing and verify
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<LedgerJournal> journal = ledgerJournalRepository.findByTransactionId(event.transactionId());
            assertTrue(journal.isPresent());

            List<LedgerEntry> entries = ledgerEntryRepository.findByJournal_Id(journal.get().getId());
            assertEquals(2, entries.size());

            // Verify withdrawal has correct entry sides (one CREDIT, one DEBIT)
            long creditCount = entries.stream().filter(e -> e.getSide() == EntrySide.CREDIT).count();
            long debitCount = entries.stream().filter(e -> e.getSide() == EntrySide.DEBIT).count();
            assertEquals(1, creditCount);
            assertEquals(1, debitCount);
        });
    }

    @Test
    void shouldProcessTransferEventFromKafka() {
        // Given
        UUID receiverWalletId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        TransactionCompletedEvent event = createTransferInEvent(receiverWalletId, senderWalletId);

        // When - send event to Kafka
        kafkaTemplate.send("transaction-completed", event.walletId().toString(), event);

        // Then - wait for processing and verify
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<LedgerJournal> journal = ledgerJournalRepository.findByTransactionId(event.transactionId());
            assertTrue(journal.isPresent());

            List<LedgerEntry> entries = ledgerEntryRepository.findByJournal_Id(journal.get().getId());
            assertEquals(2, entries.size());
        });
    }

    @Test
    void shouldHandleIdempotencyFromKafka() {
        // Given
        TransactionCompletedEvent event = createDepositEvent();

        // When - send same event multiple times
        kafkaTemplate.send("transaction-completed", event.walletId().toString(), event);
        kafkaTemplate.send("transaction-completed", event.walletId().toString(), event);
        kafkaTemplate.send("transaction-completed", event.walletId().toString(), event);

        // Then - wait and verify only one journal/entry set created
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<LedgerJournal> journals = ledgerJournalRepository.findAll().stream()
                    .filter(j -> j.getTransactionId().equals(event.transactionId()))
                    .toList();
            assertEquals(1, journals.size());
        });
    }

    // ========== DOUBLE-ENTRY BALANCE VERIFICATION ==========

    @Test
    void shouldMaintainDoubleEntryBalance() {
        // Given - multiple transactions
        TransactionCompletedEvent deposit1 = createDepositEventWithAmount(new BigDecimal("100.00"));
        TransactionCompletedEvent deposit2 = createDepositEventWithAmount(new BigDecimal("50.00"));
        TransactionCompletedEvent withdrawal = createWithdrawalEventWithAmount(new BigDecimal("30.00"));

        // When
        ledgerService.processTransaction(deposit1);
        ledgerService.processTransaction(deposit2);
        ledgerService.processTransaction(withdrawal);

        // Then - sum of all DEBITs should equal sum of all CREDITs
        List<LedgerEntry> allEntries = ledgerEntryRepository.findAll();

        BigDecimal totalDebits = allEntries.stream()
                .filter(e -> e.getSide() == EntrySide.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = allEntries.stream()
                .filter(e -> e.getSide() == EntrySide.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalDebits.compareTo(totalCredits),
                "Double-entry balance violated: DEBITs=" + totalDebits + ", CREDITs=" + totalCredits);
    }

    @Test
    void shouldMaintainDoubleEntryBalanceWithTransfers() {
        // Given - deposits and transfers
        UUID wallet1 = UUID.randomUUID();
        UUID wallet2 = UUID.randomUUID();

        TransactionCompletedEvent deposit1 = createDepositEventForWallet(wallet1);
        TransactionCompletedEvent deposit2 = createDepositEventForWallet(wallet2);
        TransactionCompletedEvent transfer = createTransferInEvent(wallet1, wallet2);

        // When
        ledgerService.processTransaction(deposit1);
        ledgerService.processTransaction(deposit2);
        ledgerService.processTransaction(transfer);

        // Then - balance should still hold
        List<LedgerEntry> allEntries = ledgerEntryRepository.findAll();

        BigDecimal totalDebits = allEntries.stream()
                .filter(e -> e.getSide() == EntrySide.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = allEntries.stream()
                .filter(e -> e.getSide() == EntrySide.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalDebits.compareTo(totalCredits),
                "Double-entry balance violated after transfer");
    }

    // ========== HELPER METHODS ==========

    private TransactionCompletedEvent createDepositEvent() {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("100.00"))
                .completedAt(Instant.now())
                .build();
    }

    private TransactionCompletedEvent createDepositEventForWallet(UUID walletId) {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(walletId)
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("100.00"))
                .completedAt(Instant.now())
                .build();
    }

    private TransactionCompletedEvent createDepositEventWithAmount(BigDecimal amount) {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(amount)
                .currency("USD")
                .balanceAfter(amount)
                .completedAt(Instant.now())
                .build();
    }

    private TransactionCompletedEvent createWithdrawalEvent() {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.WITHDRAWAL)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("50.00"))
                .completedAt(Instant.now())
                .build();
    }

    private TransactionCompletedEvent createWithdrawalEventWithAmount(BigDecimal amount) {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.WITHDRAWAL)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(amount)
                .currency("USD")
                .balanceAfter(new BigDecimal("100.00").subtract(amount))
                .completedAt(Instant.now())
                .build();
    }

    private TransactionCompletedEvent createTransferInEvent(UUID receiverWalletId, UUID senderWalletId) {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.TRANSFER_IN)
                .walletId(receiverWalletId)
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("175.00"))
                .relatedWalletId(senderWalletId)
                .completedAt(Instant.now())
                .build();
    }

    private TransactionCompletedEvent createTransferOutEvent() {
        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .type(TransactionType.TRANSFER_OUT)
                .walletId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .balanceAfter(new BigDecimal("25.00"))
                .relatedWalletId(UUID.randomUUID())
                .completedAt(Instant.now())
                .build();
    }
}
