package com.patorinaldi.wallet.account.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.patorinaldi.wallet.common.event.UserRegisteredEvent;
import com.patorinaldi.wallet.common.event.WalletCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.debug("Publishing UserRegisteredEvent for userId: {}", event.userId());
        kafkaTemplate.send("user-registered", event.userId().toString(), event);
        log.info("Published UserRegisteredEvent for userId: {}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleWalletCreated(WalletCreatedEvent event) {
        log.debug("Publishing WalletCreatedEvent for walletId: {}", event.walletId());
        kafkaTemplate.send("wallet-created", event.walletId().toString(), event);
        log.info("Published WalletCreatedEvent for walletId: {}", event.walletId());
    }
    
}
