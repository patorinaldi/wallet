package com.patorinaldi.wallet.ledger.service;

import com.patorinaldi.wallet.common.event.TransactionCompletedEvent;
import com.patorinaldi.wallet.ledger.entity.*;
import com.patorinaldi.wallet.ledger.repository.LedgerAccountRepository;
import com.patorinaldi.wallet.ledger.repository.LedgerEntryRepository;
import com.patorinaldi.wallet.ledger.repository.LedgerJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerJournalRepository journalRepository;

    @Transactional
    public void processTransaction(TransactionCompletedEvent event) {

        log.info("Processing transaction with ID: {}", event.transactionId());

        if (journalRepository.existsByTransactionId(event.transactionId())) {
            log.warn("Transaction with ID: {} has already been processed. Skipping.", event.transactionId());
            return;
        }

        LedgerJournal journal = LedgerJournal.builder()
                .transactionId(event.transactionId())
                .description(event.type() + " - " + event.amount() + " " + event.currency())
                .build();
        journal = journalRepository.save(journal);

        switch (event.type()) {
            case DEPOSIT -> createDepositEntries(event, journal);
            case WITHDRAWAL -> createWithdrawalEntries(event, journal);
            case TRANSFER_IN -> createTransferEntries(event, journal);
            case TRANSFER_OUT -> {
                // Skip - recorded with TRANSFER_IN
                log.debug("Skipping TRANSFER_OUT, will be recorded with TRANSFER_IN");
            }
        }

    }

    private void createTransferEntries(TransactionCompletedEvent event, LedgerJournal journal) {

        LedgerAccount userAccount = getOrCreateUserAccount(event.walletId(), event.currency());
        LedgerAccount senderAccount = getOrCreateUserAccount(event.relatedWalletId(), event.currency());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(event.transactionId())
                .journal(journal)
                .account(userAccount)
                .amount(event.amount())
                .side(EntrySide.DEBIT)
                .currency(event.currency())
                .description("Transfer In")
                .reportedBalanceAfter(event.balanceAfter())
                .recordedAt(Instant.now())
                .sourceEvent("transaction-completed")
                .eventTimestamp(event.completedAt())
                .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(event.transactionId())
                .journal(journal)
                .account(senderAccount)
                .amount(event.amount())
                .side(EntrySide.CREDIT)
                .currency(event.currency())
                .description("Transfer Out")
                .recordedAt(Instant.now())
                .sourceEvent("transaction-completed")
                .eventTimestamp(event.completedAt())
                .build());

        log.info("Created transfer entries for transaction: {}", event.transactionId());
    }

    private void createWithdrawalEntries(TransactionCompletedEvent event, LedgerJournal journal) {

        LedgerAccount userAccount = getOrCreateUserAccount(event.walletId(), event.currency());
        LedgerAccount bankAccount = getSystemAccount(AccountType.SYSTEM_BANK, event.currency());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(event.transactionId())
                .journal(journal)
                .account(userAccount)
                .amount(event.amount())
                .side(EntrySide.CREDIT)
                .currency(event.currency())
                .description("Withdraw")
                .reportedBalanceAfter(event.balanceAfter())
                .recordedAt(Instant.now())
                .sourceEvent("transaction-completed")
                .eventTimestamp(event.completedAt())
                .build());


        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(event.transactionId())
                .journal(journal)
                .account(bankAccount)
                .amount(event.amount())
                .side(EntrySide.DEBIT)
                .currency(event.currency())
                .description("Withdraw funding")
                .recordedAt(Instant.now())
                .sourceEvent("transaction-completed")
                .eventTimestamp(event.completedAt())
                .build());

        log.info("Created withdraw entries for transaction: {}", event.transactionId());

    }

    private void createDepositEntries(TransactionCompletedEvent event, LedgerJournal journal) {

        LedgerAccount userAccount = getOrCreateUserAccount(event.walletId(), event.currency());
        LedgerAccount bankAccount = getSystemAccount(AccountType.SYSTEM_BANK, event.currency());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(event.transactionId())
                .journal(journal)
                .account(userAccount)
                .amount(event.amount())
                .side(EntrySide.DEBIT)
                .currency(event.currency())
                .description("Deposit")
                .reportedBalanceAfter(event.balanceAfter())
                .recordedAt(Instant.now())
                .sourceEvent("transaction-completed")
                .eventTimestamp(event.completedAt())
                .build());


        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(event.transactionId())
                .journal(journal)
                .account(bankAccount)
                .amount(event.amount())
                .side(EntrySide.CREDIT)
                .currency(event.currency())
                .description("Deposit funding")
                .recordedAt(Instant.now())
                .sourceEvent("transaction-completed")
                .eventTimestamp(event.completedAt())
                .build());

        log.info("Created deposit entries for transaction: {}", event.transactionId());
    }

    private LedgerAccount getOrCreateUserAccount(UUID walletId, String currency) {
        return ledgerAccountRepository.findByExternalIdAndCurrency(walletId, currency)
                .orElseGet(() -> {
                    LedgerAccount account = LedgerAccount.builder()
                            .accountType(AccountType.USER_WALLET)
                            .accountNumber("WALLET-" + walletId)
                            .externalId(walletId)
                            .currency(currency)
                            .build();
                    return ledgerAccountRepository.save(account);
                });
    }

    private LedgerAccount getSystemAccount(AccountType type, String currency) {
        return ledgerAccountRepository.findByAccountTypeAndCurrency(type, currency)
                .orElseThrow(() -> new IllegalStateException(
                        "System account not found: " + type + " " + currency));
    }
}
