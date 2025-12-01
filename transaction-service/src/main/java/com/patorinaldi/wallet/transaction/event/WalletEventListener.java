package com.patorinaldi.wallet.transaction.event;

import com.patorinaldi.wallet.common.event.WalletCreatedEvent;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import com.patorinaldi.wallet.transaction.repository.WalletBalanceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletEventListener {

    private final WalletBalanceRepository walletBalanceRepository;

    @KafkaListener(topics = "wallet-created", groupId = "transaction-service")
    @Transactional
    public void handleWalletCreated(WalletCreatedEvent event) {

        log.info("Received WalletCreatedEvent for wallet: {}, user: {}, currency: {}",
                event.walletId(), event.userId(), event.currency());

        if (walletBalanceRepository.existsByWalletId(event.walletId())) {
            log.warn("Balance already exists for wallet: {}, skipping creation",
                    event.walletId());
            return;
        }

        WalletBalance balance = WalletBalance.builder()
                .walletId(event.walletId())
                .userId(event.userId())
                .currency(event.currency())
                .balance(BigDecimal.ZERO)
                .build();

        walletBalanceRepository.save(balance);

        log.info("Created balance for wallet: {} with currency: {}, initial balance: 0.00",
                event.walletId(), event.currency());
    }
}
