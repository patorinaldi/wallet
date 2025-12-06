package com.patorinaldi.wallet.ledger.config;

import com.patorinaldi.wallet.ledger.entity.AccountType;
import com.patorinaldi.wallet.ledger.entity.LedgerAccount;
import com.patorinaldi.wallet.ledger.repository.LedgerAccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAccountSeeder implements CommandLineRunner {

    private final LedgerAccountRepository ledgerAccountRepository;

    private static final List<String> SUPPORTED_CURRENCIES = List.of("USD");

    private static final List<AccountType> SYSTEM_ACCOUNT_TYPES = List.of(
            AccountType.SYSTEM_BANK,
            AccountType.SYSTEM_FEES,
            AccountType.SYSTEM_SUSPENSE
    );

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        log.info("Checking system accounts...");
        for (AccountType type : SYSTEM_ACCOUNT_TYPES) {
            for (String currency : SUPPORTED_CURRENCIES) {
                String accountNumber = type.name() + "-" + currency;
                if (!ledgerAccountRepository.existsByAccountNumber(accountNumber)) {
                    LedgerAccount account = LedgerAccount.builder()
                            .accountType(type)
                            .accountNumber(accountNumber)
                            .currency(currency)
                            .build();
                    ledgerAccountRepository.save(account);
                    log.info("Created system account: {} ({})", accountNumber, currency);
                } else {
                    log.debug("System account already exists: {}", accountNumber);
                }
            }
        }
        log.info("System accounts verified successfully");
    }
}
