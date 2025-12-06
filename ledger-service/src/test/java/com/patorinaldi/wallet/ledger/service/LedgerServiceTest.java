package com.patorinaldi.wallet.ledger.service;

import com.patorinaldi.wallet.common.enums.TransactionType;
import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.ledger.entity.*;
import com.patorinaldi.wallet.ledger.repository.LedgerAccountRepository;
import com.patorinaldi.wallet.ledger.repository.LedgerEntryRepository;
import com.patorinaldi.wallet.ledger.repository.LedgerJournalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerAccountRepository ledgerAccountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerJournalRepository journalRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<LedgerEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<LedgerJournal> journalCaptor;

    // ========== IDEMPOTENCY TESTS ==========

    @Test
    void processTransaction_shouldSkip_whenAlreadyProcessed() {
        // Given
        TransactionCompletedEvent event = createDepositEvent();

        when(journalRepository.existsByTransactionId(event.transactionId())).thenReturn(true);

        // When
        ledgerService.processTransaction(event);

        // Then
        verify(journalRepository).existsByTransactionId(event.transactionId());
        verify(journalRepository, never()).save(any(LedgerJournal.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    // ========== DEPOSIT TESTS ==========

    @Test
    void processTransaction_shouldCreateJournalAndEntries_forDeposit() {
        // Given
        TransactionCompletedEvent event = createDepositEvent();
        LedgerAccount userAccount = createUserAccount(event.walletId(), event.currency());
        LedgerAccount bankAccount = createSystemAccount(AccountType.SYSTEM_BANK, event.currency());
        LedgerJournal savedJournal = createJournal(event.transactionId());

        when(journalRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(journalRepository.save(any(LedgerJournal.class))).thenReturn(savedJournal);
        when(ledgerAccountRepository.findByExternalIdAndCurrency(event.walletId(), event.currency()))
                .thenReturn(Optional.of(userAccount));
        when(ledgerAccountRepository.findByAccountTypeAndCurrency(AccountType.SYSTEM_BANK, event.currency()))
                .thenReturn(Optional.of(bankAccount));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ledgerService.processTransaction(event);

        // Then
        verify(journalRepository).existsByTransactionId(event.transactionId());
        verify(journalRepository).save(journalCaptor.capture());
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        // Verify journal
        LedgerJournal capturedJournal = journalCaptor.getValue();
        assertEquals(event.transactionId(), capturedJournal.getTransactionId());
        assertTrue(capturedJournal.getDescription().contains("DEPOSIT"));

        // Verify entries
        List<LedgerEntry> entries = entryCaptor.getAllValues();
        assertEquals(2, entries.size());

        // Verify DEBIT entry (user account)
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.DEBIT)
                .findFirst()
                .orElseThrow();
        assertEquals(userAccount, debitEntry.getAccount());
        assertEquals(event.amount(), debitEntry.getAmount());
        assertEquals(event.currency(), debitEntry.getCurrency());
        assertEquals(event.balanceAfter(), debitEntry.getReportedBalanceAfter());
        assertEquals("transaction-completed", debitEntry.getSourceEvent());

        // Verify CREDIT entry (bank account)
        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.CREDIT)
                .findFirst()
                .orElseThrow();
        assertEquals(bankAccount, creditEntry.getAccount());
        assertEquals(event.amount(), creditEntry.getAmount());
        assertEquals(event.currency(), creditEntry.getCurrency());
    }

    // ========== WITHDRAWAL TESTS ==========

    @Test
    void processTransaction_shouldCreateJournalAndEntries_forWithdrawal() {
        // Given
        TransactionCompletedEvent event = createWithdrawalEvent();
        LedgerAccount userAccount = createUserAccount(event.walletId(), event.currency());
        LedgerAccount bankAccount = createSystemAccount(AccountType.SYSTEM_BANK, event.currency());
        LedgerJournal savedJournal = createJournal(event.transactionId());

        when(journalRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(journalRepository.save(any(LedgerJournal.class))).thenReturn(savedJournal);
        when(ledgerAccountRepository.findByExternalIdAndCurrency(event.walletId(), event.currency()))
                .thenReturn(Optional.of(userAccount));
        when(ledgerAccountRepository.findByAccountTypeAndCurrency(AccountType.SYSTEM_BANK, event.currency()))
                .thenReturn(Optional.of(bankAccount));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ledgerService.processTransaction(event);

        // Then
        verify(journalRepository).save(any(LedgerJournal.class));
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        List<LedgerEntry> entries = entryCaptor.getAllValues();
        assertEquals(2, entries.size());

        // Verify CREDIT entry (user account - money leaving)
        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.CREDIT)
                .findFirst()
                .orElseThrow();
        assertEquals(userAccount, creditEntry.getAccount());
        assertEquals(event.amount(), creditEntry.getAmount());

        // Verify DEBIT entry (bank account - money going out)
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.DEBIT)
                .findFirst()
                .orElseThrow();
        assertEquals(bankAccount, debitEntry.getAccount());
        assertEquals(event.amount(), debitEntry.getAmount());
    }

    // ========== TRANSFER TESTS ==========

    @Test
    void processTransaction_shouldCreateJournalAndEntries_forTransferIn() {
        // Given
        UUID receiverWalletId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        TransactionCompletedEvent event = createTransferInEvent(receiverWalletId, senderWalletId);
        LedgerAccount receiverAccount = createUserAccount(receiverWalletId, event.currency());
        LedgerAccount senderAccount = createUserAccount(senderWalletId, event.currency());
        LedgerJournal savedJournal = createJournal(event.transactionId());

        when(journalRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(journalRepository.save(any(LedgerJournal.class))).thenReturn(savedJournal);
        when(ledgerAccountRepository.findByExternalIdAndCurrency(receiverWalletId, event.currency()))
                .thenReturn(Optional.of(receiverAccount));
        when(ledgerAccountRepository.findByExternalIdAndCurrency(senderWalletId, event.currency()))
                .thenReturn(Optional.of(senderAccount));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ledgerService.processTransaction(event);

        // Then
        verify(journalRepository).save(any(LedgerJournal.class));
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        List<LedgerEntry> entries = entryCaptor.getAllValues();
        assertEquals(2, entries.size());

        // Verify DEBIT entry (receiver account - money coming in)
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.DEBIT)
                .findFirst()
                .orElseThrow();
        assertEquals(receiverAccount, debitEntry.getAccount());
        assertEquals(event.amount(), debitEntry.getAmount());

        // Verify CREDIT entry (sender account - money going out)
        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getSide() == EntrySide.CREDIT)
                .findFirst()
                .orElseThrow();
        assertEquals(senderAccount, creditEntry.getAccount());
        assertEquals(event.amount(), creditEntry.getAmount());
    }

    @Test
    void processTransaction_shouldSkipEntries_forTransferOut() {
        // Given
        TransactionCompletedEvent event = createTransferOutEvent();

        when(journalRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(journalRepository.save(any(LedgerJournal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ledgerService.processTransaction(event);

        // Then
        verify(journalRepository).save(any(LedgerJournal.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    // ========== USER ACCOUNT CREATION TESTS ==========

    @Test
    void processTransaction_shouldCreateUserAccount_whenNotExists() {
        // Given
        TransactionCompletedEvent event = createDepositEvent();
        LedgerAccount bankAccount = createSystemAccount(AccountType.SYSTEM_BANK, event.currency());
        LedgerJournal savedJournal = createJournal(event.transactionId());

        when(journalRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(journalRepository.save(any(LedgerJournal.class))).thenReturn(savedJournal);
        when(ledgerAccountRepository.findByExternalIdAndCurrency(event.walletId(), event.currency()))
                .thenReturn(Optional.empty());
        when(ledgerAccountRepository.save(any(LedgerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerAccountRepository.findByAccountTypeAndCurrency(AccountType.SYSTEM_BANK, event.currency()))
                .thenReturn(Optional.of(bankAccount));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ledgerService.processTransaction(event);

        // Then
        ArgumentCaptor<LedgerAccount> accountCaptor = ArgumentCaptor.forClass(LedgerAccount.class);
        verify(ledgerAccountRepository).save(accountCaptor.capture());

        LedgerAccount createdAccount = accountCaptor.getValue();
        assertEquals(AccountType.USER_WALLET, createdAccount.getAccountType());
        assertEquals(event.walletId(), createdAccount.getExternalId());
        assertEquals(event.currency(), createdAccount.getCurrency());
        assertTrue(createdAccount.getAccountNumber().contains(event.walletId().toString()));
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    void processTransaction_shouldThrowException_whenSystemAccountMissing() {
        // Given
        TransactionCompletedEvent event = createDepositEvent();
        LedgerAccount userAccount = createUserAccount(event.walletId(), event.currency());
        LedgerJournal savedJournal = createJournal(event.transactionId());

        when(journalRepository.existsByTransactionId(event.transactionId())).thenReturn(false);
        when(journalRepository.save(any(LedgerJournal.class))).thenReturn(savedJournal);
        when(ledgerAccountRepository.findByExternalIdAndCurrency(event.walletId(), event.currency()))
                .thenReturn(Optional.of(userAccount));
        when(ledgerAccountRepository.findByAccountTypeAndCurrency(AccountType.SYSTEM_BANK, event.currency()))
                .thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ledgerService.processTransaction(event));

        assertTrue(exception.getMessage().contains("System account not found"));
        assertTrue(exception.getMessage().contains("SYSTEM_BANK"));
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

    private LedgerAccount createUserAccount(UUID walletId, String currency) {
        return LedgerAccount.builder()
                .id(UUID.randomUUID())
                .accountType(AccountType.USER_WALLET)
                .accountNumber("WALLET-" + walletId)
                .externalId(walletId)
                .currency(currency)
                .build();
    }

    private LedgerAccount createSystemAccount(AccountType type, String currency) {
        return LedgerAccount.builder()
                .id(UUID.randomUUID())
                .accountType(type)
                .accountNumber(type.name() + "-" + currency)
                .currency(currency)
                .build();
    }

    private LedgerJournal createJournal(UUID transactionId) {
        return LedgerJournal.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .description("Test journal")
                .build();
    }
}
