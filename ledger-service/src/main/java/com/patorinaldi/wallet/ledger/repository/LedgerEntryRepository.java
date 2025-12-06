package com.patorinaldi.wallet.ledger.repository;

import com.patorinaldi.wallet.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccount_IdOrderByRecordedAtDesc(UUID accountId);

    List<LedgerEntry> findByJournal_Id(UUID journalId);
}
