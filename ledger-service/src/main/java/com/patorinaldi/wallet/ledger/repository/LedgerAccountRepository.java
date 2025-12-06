package com.patorinaldi.wallet.ledger.repository;

import com.patorinaldi.wallet.ledger.entity.AccountType;
import com.patorinaldi.wallet.ledger.entity.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByAccountTypeAndCurrency(AccountType accountType, String currency);

    Optional<LedgerAccount> findByExternalIdAndCurrency(UUID externalId, String currency);

    boolean existsByAccountNumber(String accountNumber);

}