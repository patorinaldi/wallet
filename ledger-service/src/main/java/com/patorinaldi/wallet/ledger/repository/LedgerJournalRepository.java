package com.patorinaldi.wallet.ledger.repository;

import com.patorinaldi.wallet.ledger.entity.LedgerJournal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerJournalRepository extends JpaRepository<LedgerJournal, UUID> {

    boolean existsByTransactionId(UUID transactionId);

    Optional<LedgerJournal> findByTransactionId(UUID transactionId);

}
